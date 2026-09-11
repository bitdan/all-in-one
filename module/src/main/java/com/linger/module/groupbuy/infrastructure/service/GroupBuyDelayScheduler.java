package com.linger.module.groupbuy.infrastructure.service;

import com.linger.module.groupbuy.activity.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.infrastructure.entity.GroupBuyDelayTaskEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.infrastructure.model.GroupBuyDelayTaskType;
import com.linger.module.groupbuy.transaction.service.GroupBuyTransactionStore;
import com.linger.module.timeWheel.TimeWheelScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 可靠延迟任务调度器。
 *
 * <p>PostgreSQL 是任务事实来源，时间轮只是未来 30 秒任务的内存加速层。节点死亡后，CLAIMED/RUNNING
 * 任务会在租约到期后被其他节点重新装载，不依赖单机内存保证可靠性。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyDelayScheduler {

    private static final long LOAD_AHEAD_SECONDS = 30L;
    private static final long LOCK_SECONDS = 90L;

    private final GroupBuyTransactionStore store;
    private final RedisGroupBuyAdmissionService admissionService;
    private final TimeWheelScheduler timeWheel = new TimeWheelScheduler(100L, 100);
    private final String workerId = "delay-" + ManagementFactory.getRuntimeMXBean().getName()
            + "-" + UUID.randomUUID().toString().substring(0, 8);

    @PostConstruct
    public void start() {
        timeWheel.start();
    }

    @PreDestroy
    public void stop() {
        timeWheel.stop();
    }

    @Scheduled(fixedDelayString = "${groupbuy.transaction.delay-poll-ms:1000}")
    public void loadUpcomingTasks() {
        // 数据库抢占与 worker 租约避免多个应用节点重复装载同一任务。
        List<GroupBuyDelayTaskEntity> tasks = store.claimDelayTasks(
                workerId, 200, LOAD_AHEAD_SECONDS, LOCK_SECONDS);
        OffsetDateTime now = now();
        for (GroupBuyDelayTaskEntity task : tasks) {
            long delayMillis = Math.max(0L, Duration.between(now, task.getExecuteAt()).toMillis());
            timeWheel.schedule(delayMillis, TimeUnit.MILLISECONDS, () -> execute(task));
        }
    }

    @Scheduled(fixedDelayString = "${groupbuy.transaction.reconcile-ms:30000}")
    public void reconcileInterruptedOrders() {
        // 覆盖两段典型崩溃窗口：INIT 后未完成 Redis/DB 确认，以及支付超时任务尚未执行。
        OffsetDateTime staleBefore = now().minusSeconds(30);
        store.findStaleInitOrders(staleBefore, 200)
                .forEach(order -> store.rejectStaleInit(order.getId()));
        store.findExpiredUnpaidOrders(200)
                .forEach(order -> store.cancelUnpaidOrder(order.getId()));
    }

    private void execute(GroupBuyDelayTaskEntity task) {
        if (!store.markDelayTaskRunning(task.getId(), workerId)) {
            return;
        }
        try {
            if (GroupBuyDelayTaskType.PAYMENT_TIMEOUT.equals(task.getTaskType())) {
                store.cancelUnpaidOrder(task.getBusinessId());
            } else if (GroupBuyDelayTaskType.GROUP_TIMEOUT.equals(task.getTaskType())) {
                expireGroup(Long.valueOf(task.getBusinessId()));
            } else {
                throw new IllegalArgumentException("未知延迟任务类型: " + task.getTaskType());
            }
            store.markDelayTaskDone(task.getId(), workerId);
        } catch (Exception e) {
            log.error("执行拼团延迟任务失败, taskId={}, type={}", task.getId(), task.getTaskType(), e);
            store.markDelayTaskFailed(task.getId(), workerId, task.getRetryCount(), e);
        }
    }

    private void expireGroup(Long groupId) {
        GroupBuyGroupEntity group = store.findGroup(groupId);
        if (group == null || group.getStatus() != com.linger.module.groupbuy.group.model.GroupInstanceStatus.OPEN) {
            return;
        }
        GroupBuyActivityEntity activity = store.findActivity(group.getActivityId());
        if (activity == null) {
            throw new IllegalStateException("团对应活动不存在, groupId=" + groupId);
        }
        if (!admissionService.failGroup(activity, group)) {
            // Redis 已成团时，等待 ORDER_PAID Outbox 把数据库推进到 SUCCESS，避免误判失败。
            throw new IllegalStateException("Redis 团状态已变化, groupId=" + groupId);
        }
        store.failExpiredGroup(groupId);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
