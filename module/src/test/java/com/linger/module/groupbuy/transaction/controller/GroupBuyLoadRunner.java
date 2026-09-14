package com.linger.module.groupbuy.transaction.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/** 有界发压器：失败后停止发送，统计截止于清理之前，清理异常不能覆盖首个失败。 */
final class GroupBuyLoadRunner {

    private GroupBuyLoadRunner() {
    }

    static <T> Result<T> run(int taskCount, int threads, int queueCapacity, int targetQps,
                              long drainTimeoutMs, long shutdownTimeoutMs,
                              BiFunction<Integer, Long, T> action, Runnable cancelRequests) {
        if (taskCount <= 0 || threads <= 0 || queueCapacity <= 0 || targetQps < 0
                || drainTimeoutMs <= 0 || shutdownTimeoutMs <= 0) {
            throw new IllegalArgumentException("请求数、线程数、队列和超时必须为正，QPS不能为负");
        }
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), task -> {
                    Thread thread = new Thread(task, "groupbuy-load-" + threadNumber.incrementAndGet());
                    // 最后的兜底；若取消后仍未退出，调用方必须阻止后续档位继续运行。
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch done = new CountDownLatch(taskCount);
        AtomicBoolean stopping = new AtomicBoolean();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        List<T> responses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Object monitor = new Object();
        AtomicInteger completed = new AtomicInteger();
        int submitted = 0;
        boolean interrupted = false;
        long startedAt = System.nanoTime();
        Result<T> result;
        try {
            executor.prestartAllCoreThreads();
            startedAt = System.nanoTime();
            for (int i = 0; i < taskCount && firstFailure.get() == null; i++) {
                final int index = i;
                final long scheduledAt = targetQps == 0 ? startedAt
                        : startedAt + i * TimeUnit.SECONDS.toNanos(1) / targetQps;
                long remaining;
                while ((remaining = scheduledAt - System.nanoTime()) > 0) {
                    TimeUnit.NANOSECONDS.sleep(remaining);
                }
                executor.execute(() -> {
                    try {
                        if (stopping.get()) {
                            return;
                        }
                        T response = action.apply(index, scheduledAt);
                        synchronized (monitor) {
                            responses.add(response);
                            completed.incrementAndGet();
                        }
                    } catch (Exception failure) {
                        synchronized (monitor) {
                            firstFailure.compareAndSet(null, failure);
                            errors.add("task=" + index + ", " + failure);
                            completed.incrementAndGet();
                        }
                    } finally {
                        done.countDown();
                    }
                });
                submitted++;
            }
        } catch (InterruptedException failure) {
            interrupted = true;
            recordFailure(monitor, errors, firstFailure, failure);
        } catch (RejectedExecutionException failure) {
            recordFailure(monitor, errors, firstFailure,
                    new IllegalStateException("CLIENT_QUEUE_FULL：客户端积压超过有界队列，已停止发压", failure));
        } finally {
            // 没有提交的任务不会触发工作线程的 finally。
            for (int i = submitted; i < taskCount; i++) {
                done.countDown();
            }
            try {
                if (!interrupted && !done.await(drainTimeoutMs, TimeUnit.MILLISECONDS)) {
                    recordFailure(monitor, errors, firstFailure,
                            new IllegalStateException("BATCH_TIMEOUT：发压结束后" + drainTimeoutMs
                                    + "ms仍有请求未完成，submitted=" + submitted + ", completed=" + completed.get()));
                }
            } catch (InterruptedException failure) {
                interrupted = true;
                recordFailure(monitor, errors, firstFailure, failure);
            }
            synchronized (monitor) {
                // 冻结统计；随后取消请求产生的响应/异常不混入本次吞吐与延迟样本。
                result = new Result<>(new ArrayList<>(responses), new ArrayList<>(errors), firstFailure.get(),
                        taskCount, submitted, completed.get(), System.nanoTime() - startedAt);
            }
            stopping.set(true);
            result.cancelledQueued = executor.shutdownNow().size();
            try {
                cancelRequests.run();
            } catch (RuntimeException failure) {
                result.addCleanupFailure(failure);
            }
            try {
                result.terminated = executor.awaitTermination(shutdownTimeoutMs, TimeUnit.MILLISECONDS);
                if (!result.terminated) {
                    result.addCleanupFailure(new IllegalStateException("CLEANUP_TIMEOUT：取消HTTP后线程池仍未退出"));
                }
            } catch (InterruptedException failure) {
                interrupted = true;
                result.addCleanupFailure(failure);
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return result;
    }

    private static void recordFailure(Object monitor, List<String> errors,
                                       AtomicReference<Throwable> firstFailure, Throwable failure) {
        synchronized (monitor) {
            firstFailure.compareAndSet(null, failure);
            errors.add(failure.toString());
        }
    }

    @Getter
    @RequiredArgsConstructor
    static final class Result<T> {
        private final List<T> responses;
        private final List<String> errors;
        private final Throwable firstFailure;
        private final int planned;
        private final int submitted;
        private final int completed;
        private final long elapsedNanos;
        private int cancelledQueued;
        private boolean terminated;

        private void addCleanupFailure(Throwable failure) {
            errors.add(failure.toString());
            if (firstFailure != null && firstFailure != failure) {
                firstFailure.addSuppressed(failure);
            }
        }
    }
}
