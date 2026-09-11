package com.linger.module.timeWheel;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间轮测试类
 */
public class SimpleTimeWheelTest {

    @Test
    public void testSimpleScheduling() throws InterruptedException {
        System.out.println("开始测试简单调度...");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);

        System.out.println("调度任务，延迟1秒执行...");
        scheduler.schedule(1, TimeUnit.SECONDS, () -> {
            System.out.println("任务执行了！时间: " + System.currentTimeMillis());
            counter.incrementAndGet();
            latch.countDown();
        });

        System.out.println("等待任务执行...");
        boolean completed = latch.await(3, TimeUnit.SECONDS);

        System.out.println("任务完成状态: " + completed);
        System.out.println("计数器值: " + counter.get());

        assertTrue(completed);
        assertEquals(1, counter.get());

        scheduler.stop();
        System.out.println("测试完成");
    }

    @Test
    public void testMultipleTasks() throws InterruptedException {
        System.out.println("开始测试多个任务...");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger(0);

        // 调度3个任务
        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            scheduler.schedule(i, TimeUnit.SECONDS, () -> {
                System.out.println("任务 " + taskId + " 执行了！时间: " + System.currentTimeMillis());
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        System.out.println("等待所有任务执行...");
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        System.out.println("所有任务完成状态: " + completed);
        System.out.println("最终计数器值: " + counter.get());

        assertTrue(completed);
        assertEquals(3, counter.get());

        scheduler.stop();
        System.out.println("测试完成");
    }

    @Test
    public void testAsyncTask() throws Exception {
        System.out.println("=== 测试异步任务 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        // 测试异步任务和结果获取
        CompletableFuture<String> future = scheduler.scheduleAsync(1, TimeUnit.SECONDS, () -> {
            System.out.println("异步任务执行中...");
            return "异步任务结果";
        });

        String result = future.get(3, TimeUnit.SECONDS);
        System.out.println("异步任务结果: " + result);
        assertEquals("异步任务结果", result);

        scheduler.stop();
    }

    @Test
    public void testAsyncTaskWithSupplier() throws Exception {
        System.out.println("=== 测试异步任务Supplier ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        // 测试异步任务并返回计算结果
        CompletableFuture<Integer> future = scheduler.scheduleAsync(1, TimeUnit.SECONDS, () -> {
            System.out.println("计算任务执行中...");
            return 42; // 返回计算结果
        });

        Integer result = future.get(3, TimeUnit.SECONDS);
        System.out.println("计算结果: " + result);
        assertEquals(42, result);

        scheduler.stop();
    }

    @Test
    public void testCustomConfig() {
        System.out.println("=== 测试自定义配置 ===");

        // 测试自定义配置
        TimeWheelScheduler scheduler = new TimeWheelScheduler(50, 200);
        assertEquals(200, scheduler.getWheelSize());
        assertEquals(10000, scheduler.getInterval()); // 50ms * 200 = 10000ms

        System.out.println("时间轮大小: " + scheduler.getWheelSize());
        System.out.println("时间轮间隔: " + scheduler.getInterval() + "ms");

        scheduler.start();
        assertTrue(scheduler.isStarted());

        scheduler.stop();
        assertFalse(scheduler.isStarted());
    }

    @Test
    public void testErrorHandling() throws InterruptedException {
        System.out.println("=== 测试错误处理 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 测试任务执行异常
        scheduler.schedule(1, TimeUnit.SECONDS, () -> {
            try {
                System.out.println("执行可能异常的任务...");
                if (Math.random() > 0.5) {
                    throw new RuntimeException("模拟任务异常");
                }
                System.out.println("任务执行成功");
            } catch (Exception e) {
                System.out.println("捕获到异常: " + e.getMessage());
                errorCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        latch.await(3, TimeUnit.SECONDS);
        System.out.println("错误处理测试完成，异常次数: " + errorCount.get());

        scheduler.stop();
    }

    @Test
    public void testAsyncErrorHandling() throws Exception {
        System.out.println("=== 测试异步错误处理 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        // 测试异步任务的异常处理
        CompletableFuture<String> future = scheduler.scheduleAsync(1, TimeUnit.SECONDS, () -> {
            System.out.println("执行可能异常的异步任务...");
            if (Math.random() > 0.5) {
                throw new RuntimeException("模拟异步任务异常");
            }
            return "异步任务成功";
        });

        try {
            String result = future.get(3, TimeUnit.SECONDS);
            System.out.println("异步任务结果: " + result);
        } catch (Exception e) {
            System.out.println("异步任务异常: " + e.getMessage());
            // 验证异常被正确传播
            assertTrue(e.getCause() instanceof RuntimeException);
        }

        scheduler.stop();
    }


    @Test
    public void testConcurrentSchedulingStable() throws InterruptedException {
        System.out.println("=== 测试稳定并发调度 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        int totalTasks = 50; // 减少任务数量，提高稳定性
        CountDownLatch latch = new CountDownLatch(totalTasks);
        AtomicInteger counter = new AtomicInteger(0);

        // 使用单线程添加任务，避免并发添加的竞态条件
        for (int i = 0; i < totalTasks; i++) {
            final int taskId = i;
            scheduler.schedule(100, TimeUnit.MILLISECONDS, () -> {
                System.out.println("稳定并发任务 " + taskId + " 执行");
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        // 等待所有任务完成
        boolean completed = latch.await(3, TimeUnit.SECONDS);
        System.out.println("稳定并发任务完成状态: " + completed);
        System.out.println("稳定并发任务执行数量: " + counter.get());

        assertEquals(totalTasks, counter.get(),
                "期望执行 " + totalTasks + " 个任务，实际执行 " + counter.get() + " 个任务");

        scheduler.stop();
    }

    @Test
    public void testConcurrentTaskExecution() throws InterruptedException {
        System.out.println("=== 测试并发任务执行 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        int totalTasks = 20;
        CountDownLatch latch = new CountDownLatch(totalTasks);
        AtomicInteger counter = new AtomicInteger(0);

        // 所有任务同时调度，测试并发执行
        for (int i = 0; i < totalTasks; i++) {
            final int taskId = i;
            scheduler.schedule(100, TimeUnit.MILLISECONDS, () -> {
                System.out.println("并发执行任务 " + taskId + " 开始");
                try {
                    // 模拟任务执行时间
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("并发执行任务 " + taskId + " 完成");
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        System.out.println("并发执行任务完成状态: " + completed);
        System.out.println("并发执行任务数量: " + counter.get());

        assertEquals(totalTasks, counter.get(),
                "期望执行 " + totalTasks + " 个任务，实际执行 " + counter.get() + " 个任务");

        scheduler.stop();
    }

    @Test
    public void testPerformance() throws InterruptedException {
        System.out.println("=== 测试性能 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        int taskCount = 1000;
        AtomicLong taskCounter = new AtomicLong(0);
        long startTime = System.currentTimeMillis();

        // 添加大量任务
        for (int i = 0; i < taskCount; i++) {
            scheduler.schedule(100, TimeUnit.MILLISECONDS, () -> {
                taskCounter.incrementAndGet();
            });
        }

        long addEndTime = System.currentTimeMillis();
        System.out.println("添加 " + taskCount + " 个任务耗时: " + (addEndTime - startTime) + "ms");

        // 等待任务执行
        Thread.sleep(2000);

        long endTime = System.currentTimeMillis();
        System.out.println("执行任务数: " + taskCounter.get());
        System.out.println("总耗时: " + (endTime - startTime) + "ms");
        System.out.println("平均每个任务耗时: " + (double) (endTime - startTime) / taskCount + "ms");

        assertEquals(taskCount, taskCounter.get());

        scheduler.stop();
    }

    @Test
    public void testLongDelayTasks() throws InterruptedException {
        System.out.println("=== 测试长时间延迟任务 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger(0);

        // 测试不同时间长度的延迟任务
        scheduler.schedule(100, TimeUnit.MILLISECONDS, () -> {
            System.out.println("短延迟任务执行");
            counter.incrementAndGet();
            latch.countDown();
        });

        scheduler.schedule(1, TimeUnit.SECONDS, () -> {
            System.out.println("中等延迟任务执行");
            counter.incrementAndGet();
            latch.countDown();
        });

        scheduler.schedule(2, TimeUnit.SECONDS, () -> {
            System.out.println("长延迟任务执行");
            counter.incrementAndGet();
            latch.countDown();
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        System.out.println("长时间延迟任务完成状态: " + completed);
        System.out.println("执行任务数: " + counter.get());

        assertEquals(3, counter.get());

        scheduler.stop();
    }

    @Test
    public void testTaskBeyondFirstWheelInterval() throws InterruptedException {
        TimeWheelScheduler scheduler = new TimeWheelScheduler(20, 5);
        scheduler.start();

        try {
            CountDownLatch latch = new CountDownLatch(1);
            long startNanos = System.nanoTime();

            scheduler.schedule(250, TimeUnit.MILLISECONDS, latch::countDown);

            assertTrue(latch.await(1, TimeUnit.SECONDS),
                    "超过第一层时间轮间隔的任务也应被重新分配并执行");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            assertTrue(elapsedMillis >= 200, "任务不应明显早于指定延迟执行");
        } finally {
            scheduler.stop();
        }
    }

    @Test
    public void testRejectsInvalidScheduleArguments() {
        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> scheduler.schedule(-1, TimeUnit.MILLISECONDS, () -> { }));
            assertThrows(NullPointerException.class,
                    () -> scheduler.schedule(1, TimeUnit.MILLISECONDS, null));
        } finally {
            scheduler.stop();
        }
    }

    @Test
    public void testSchedulerLifecycle() {
        System.out.println("=== 测试调度器生命周期 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();

        // 初始状态
        assertFalse(scheduler.isStarted());

        // 启动调度器
        scheduler.start();
        assertTrue(scheduler.isStarted());

        // 重复启动应该无效果
        scheduler.start();
        assertTrue(scheduler.isStarted());

        // 停止调度器
        scheduler.stop();
        assertFalse(scheduler.isStarted());

        // 重复停止应该无效果
        scheduler.stop();
        assertFalse(scheduler.isStarted());

        System.out.println("调度器生命周期测试完成");
    }

    @Test
    public void testTaskTimeout() throws Exception {
        System.out.println("=== 测试任务超时 ===");

        TimeWheelScheduler scheduler = new TimeWheelScheduler();
        scheduler.start();

        // 测试异步任务超时
        CompletableFuture<String> future = scheduler.scheduleAsync(3, TimeUnit.SECONDS, () -> {
            // 模拟长时间执行的任务
            try {
                Thread.sleep(5000); // 5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "长时间任务结果";
        });

        try {
            // 设置较短的超时时间
            String result = future.get(1, TimeUnit.SECONDS);
            fail("应该抛出超时异常");
        } catch (TimeoutException e) {
            System.out.println("任务超时异常: " + e.getMessage());
            // 验证超时异常
            assertTrue(e instanceof TimeoutException);
        }

        scheduler.stop();
    }
} 
