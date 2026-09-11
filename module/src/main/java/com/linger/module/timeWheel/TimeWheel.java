package com.linger.module.timeWheel;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级时间轮实现
 * 支持毫秒级精度的定时任务调度
 */
@Slf4j
public class TimeWheel {

    private final long tickMs; // 每个时间格子的毫秒数
    private final int wheelSize; // 时间轮大小
    private final long interval; // 时间轮总间隔
    private final AtomicLong currentTime; // 当前时间
    private final TimeWheelBucket[] buckets; // 时间轮桶
    private final DelayQueue<TimeWheelBucket> delayQueue; // 延迟队列

    private final ScheduledExecutorService executor; // 调度执行器
    private final AtomicBoolean running; // 运行状态
    private final boolean rootWheel;

    private volatile TimeWheel overflowWheel; // 溢出时间轮

    public TimeWheel(long tickMs, int wheelSize, long startTime) {
        this(tickMs, wheelSize, startTime, new DelayQueue<>(), true);
    }

    private TimeWheel(long tickMs, int wheelSize, long startTime,
                      DelayQueue<TimeWheelBucket> delayQueue, boolean rootWheel) {
        if (tickMs <= 0) {
            throw new IllegalArgumentException("tickMs must be greater than zero");
        }
        if (wheelSize <= 0) {
            throw new IllegalArgumentException("wheelSize must be greater than zero");
        }
        if (tickMs > Long.MAX_VALUE / wheelSize) {
            throw new IllegalArgumentException("time wheel interval is too large");
        }
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.interval = tickMs * wheelSize;
        this.currentTime = new AtomicLong(startTime - (startTime % tickMs));
        this.buckets = new TimeWheelBucket[wheelSize];
        this.delayQueue = delayQueue;
        this.rootWheel = rootWheel;
        this.executor = rootWheel ? newSchedulerExecutor() : null;
        this.running = new AtomicBoolean(false);

        // 初始化所有桶
        for (int i = 0; i < wheelSize; i++) {
            buckets[i] = new TimeWheelBucket();
        }
    }

    /**
     * 添加定时任务
     */
    public boolean addTask(TimerTask task) {
        long expiration = task.getDelayMs();
        long currentTimeMs = currentTime.get();

        if (expiration <= currentTimeMs) {
            return false;
        }

        if (expiration - currentTimeMs < interval) {
            long virtualId = (expiration / tickMs);
            if (rootWheel && expiration % tickMs != 0) {
                virtualId++;
            }
            int index = (int) (virtualId % wheelSize);
            TimeWheelBucket bucket = buckets[index];
            long bucketExpiration = virtualId * tickMs;
            synchronized (bucket) {
                bucket.addTask(task);
                if (bucket.setExpiration(bucketExpiration)) {
                    delayQueue.offer(bucket);
                }
            }
            return true;
        } else {
            if (overflowWheel == null) {
                addOverflowWheel();
            }
            return overflowWheel.addTask(task);
        }
    }

    /**
     * 推进时间轮
     */
    public void advanceClock(long timeMs) {
        if (timeMs >= currentTime.get() + tickMs) {
            currentTime.set(timeMs - (timeMs % tickMs));

            // 推进溢出时间轮
            if (overflowWheel != null) {
                overflowWheel.advanceClock(timeMs);
            }
        }
    }

    /**
     * 启动时间轮
     */
    public void start() {
        if (!rootWheel) {
            throw new IllegalStateException("Only the root time wheel can be started");
        }
        if (running.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::run, 0, tickMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 停止时间轮
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
        }
    }

    /**
     * 运行时间轮
     */
    private void run() {
        try {
            // 推进当前时间
            long currentTimeMs = System.currentTimeMillis();
            advanceClock(currentTimeMs);

            // 处理所有到期的桶
            TimeWheelBucket bucket;
            while ((bucket = delayQueue.poll()) != null) {
                List<TimerTask> tasks = bucket.drain();
                for (TimerTask task : tasks) {
                    if (task.isExpired() || !addTask(task)) {
                        task.run();
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error("Time wheel tick failed", e);
        }
    }

    /**
     * 添加溢出时间轮
     */
    private void addOverflowWheel() {
        synchronized (this) {
            if (overflowWheel == null) {
                overflowWheel = new TimeWheel(interval, wheelSize, currentTime.get(), delayQueue, false);
            }
        }
    }

    private ScheduledExecutorService newSchedulerExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "time-wheel-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 获取当前时间
     */
    public long getCurrentTime() {
        return currentTime.get();
    }

    /**
     * 获取时间轮大小
     */
    public int getWheelSize() {
        return wheelSize;
    }

    /**
     * 获取时间间隔
     */
    public long getInterval() {
        return interval;
    }
} 
