package com.linger.module.timeWheel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 时间轮桶，用于存储和管理定时任务
 */
public class TimeWheelBucket implements Delayed {

    private final TimerTaskList taskList; // 任务链表
    private final AtomicLong expiration; // 过期时间

    public TimeWheelBucket() {
        this.taskList = new TimerTaskList();
        this.expiration = new AtomicLong(-1L);
    }

    /**
     * 添加任务到桶中
     */
    public void addTask(TimerTask task) {
        taskList.addTask(task);
    }

    /**
     * 设置过期时间
     *
     * @return 如果之前未设置过过期时间则返回true
     */
    public boolean setExpiration(long expirationMs) {
        return this.expiration.compareAndSet(-1L, expirationMs);
    }

    /**
     * 获取过期时间
     */
    public long getExpiration() {
        return expiration.get();
    }

    public synchronized List<TimerTask> drain() {
        List<TimerTask> tasks = new ArrayList<>(taskList.size());
        TimerTask task = taskList.poll();
        while (task != null) {
            tasks.add(task);
            task = taskList.poll();
        }
        expiration.set(-1L);
        return tasks;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(0L, expiration.get() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }
        if (other instanceof TimeWheelBucket) {
            TimeWheelBucket that = (TimeWheelBucket) other;
            return Long.compare(expiration.get(), that.expiration.get());
        }
        return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
    }
} 
