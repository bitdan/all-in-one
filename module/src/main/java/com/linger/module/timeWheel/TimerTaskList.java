package com.linger.module.timeWheel;

/**
 * 定时任务链表，用于管理桶中的任务
 */
public class TimerTaskList {

    private final TimerTaskEntry root; // 根节点
    private int taskCounter; // 任务计数器

    public TimerTaskList() {
        this.root = new TimerTaskEntry(null);
        this.root.next = this.root;
        this.root.prev = this.root;
    }

    /**
     * 添加任务到链表
     */
    public synchronized void addTask(TimerTask task) {
        TimerTaskEntry entry = new TimerTaskEntry(task);
        addEntry(entry);
        taskCounter++;
    }

    /**
     * 从链表头部取出任务
     */
    public synchronized TimerTask poll() {
        TimerTaskEntry entry = root.next;
        if (entry == root) {
            return null; // 链表为空
        }

        removeEntry(entry);
        taskCounter--;
        return entry.getTask();
    }

    /**
     * 添加节点到链表
     */
    private void addEntry(TimerTaskEntry entry) {
        entry.next = root.next;
        entry.prev = root;
        root.next.prev = entry;
        root.next = entry;
    }

    /**
     * 从链表中移除节点
     */
    private void removeEntry(TimerTaskEntry entry) {
        entry.prev.next = entry.next;
        entry.next.prev = entry.prev;
        entry.next = null;
        entry.prev = null;
    }

    /**
     * 获取任务数量
     */
    public synchronized int size() {
        return taskCounter;
    }

    /**
     * 检查链表是否为空
     */
    public synchronized boolean isEmpty() {
        return root.next == root;
    }

    /**
     * 清空链表
     */
    public synchronized void clear() {
        while (!isEmpty()) {
            poll();
        }
    }

    /**
     * 定时任务链表节点
     */
    private static class TimerTaskEntry {
        private final TimerTask task;
        private TimerTaskEntry next;
        private TimerTaskEntry prev;

        private TimerTaskEntry(TimerTask task) {
            this.task = task;
        }

        public TimerTask getTask() {
            return task;
        }
    }
}
