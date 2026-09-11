package com.linger.module.timeWheel;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时任务
 */
@Slf4j
public class TimerTask {

    private final long delayMs; // 延迟时间（毫秒）
    private final Runnable task; // 任务执行逻辑
    private final long createTime; // 创建时间
    private final int taskId; // 任务ID

    private static final AtomicInteger TASK_ID_GENERATOR = new AtomicInteger(0);

    public TimerTask(long delayMs, Runnable task) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must not be negative");
        }
        this.createTime = System.currentTimeMillis();
        this.delayMs = Math.addExact(createTime, delayMs);
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.taskId = TASK_ID_GENERATOR.incrementAndGet();
    }

    /**
     * 获取延迟时间（毫秒）
     */
    public long getDelayMs() {
        return delayMs;
    }

    /**
     * 执行任务
     */
    public void run() {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Scheduled task failed, taskId={}", taskId, e);
        }
    }

    /**
     * 获取创建时间
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 获取任务ID
     */
    public int getTaskId() {
        return taskId;
    }

    /**
     * 检查任务是否已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= delayMs;
    }

    @Override
    public String toString() {
        return "TimerTask{" +
                "taskId=" + taskId +
                ", delayMs=" + delayMs +
                ", createTime=" + createTime +
                '}';
    }
} 
