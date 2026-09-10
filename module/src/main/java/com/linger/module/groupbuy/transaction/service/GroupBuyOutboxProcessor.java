package com.linger.module.groupbuy.transaction.service;

import com.linger.module.groupbuy.transaction.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyOutboxEventEntity;
import com.linger.module.groupbuy.transaction.model.GroupBuyEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.UUID;

/**
 * 本地事务消息处理器。
 *
 * <p>事件采用至少一次处理语义：节点通过带租约的 SKIP LOCKED SQL 抢占；处理成功前宕机会在租约到期后重投。
 * Redis 预占状态、成员状态 CAS 和支付渠道幂等键共同保证重复处理不会重复扣减或退款。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyOutboxProcessor {

    private final GroupBuyTransactionStore store;
    private final RedisGroupBuyAdmissionService admissionService;
    private final PaymentGateway paymentGateway;
    private final String workerId = "outbox-" + ManagementFactory.getRuntimeMXBean().getName()
            + "-" + UUID.randomUUID().toString().substring(0, 8);

    @Scheduled(fixedDelayString = "${groupbuy.transaction.outbox-poll-ms:500}")
    public void poll() {
        List<GroupBuyOutboxEventEntity> events = store.claimOutbox(workerId, 100);
        for (GroupBuyOutboxEventEntity event : events) {
            try {
                handle(event);
                store.markOutboxDone(event.getId(), workerId);
            } catch (Exception e) {
                log.error("处理拼团 Outbox 失败, eventId={}, eventType={}",
                        event.getId(), event.getEventType(), e);
                store.markOutboxFailed(event.getId(), workerId, event.getRetryCount(), e);
            }
        }
    }

    private void handle(GroupBuyOutboxEventEntity event) {
        switch (event.getEventType()) {
            case GroupBuyEventType.ORDER_PAID:
                handleOrderPaid(event.getAggregateId());
                break;
            case GroupBuyEventType.ORDER_RELEASE:
                handleOrderRelease(event.getAggregateId());
                break;
            case GroupBuyEventType.ORDER_REFUND:
                handleOrderRefund(event.getAggregateId());
                break;
            case GroupBuyEventType.GROUP_SUCCESS:
                log.info("团已成功，可在此接入履约消息, groupId={}", event.getAggregateId());
                break;
            default:
                throw new IllegalArgumentException("未知 Outbox 事件: " + event.getEventType());
        }
    }

    private void handleOrderPaid(String orderId) {
        GroupBuyOrderEntity order = requireOrder(orderId);
        GroupBuyActivityEntity activity = requireActivity(order.getActivityId());
        // 先推进 Redis 库存，再推进数据库团计数；中间失败由 Outbox 重试，两个步骤本身均幂等。
        RedisGroupBuyAdmissionService.PaymentConfirmation confirmation =
                admissionService.confirmPayment(activity, order);

        if (confirmation == RedisGroupBuyAdmissionService.PaymentConfirmation.RESERVATION_NOT_FOUND) {
            throw new IllegalStateException("Redis 预占记录不存在, orderId=" + orderId);
        }
        if (confirmation == RedisGroupBuyAdmissionService.PaymentConfirmation.GROUP_UNAVAILABLE) {
            store.movePaidOrderToRefunding(orderId);
            return;
        }
        store.applyPaidOrder(orderId);
    }

    private void handleOrderRelease(String orderId) {
        GroupBuyOrderEntity order = requireOrder(orderId);
        admissionService.release(requireActivity(order.getActivityId()), order);
    }

    private void handleOrderRefund(String orderId) {
        GroupBuyOrderEntity order = requireOrder(orderId);
        // 真实支付实现必须以 paymentNo 做退款幂等，允许本事件在宕机恢复后重复调用。
        if (!paymentGateway.refund(order)) {
            throw new IllegalStateException("支付渠道退款失败, orderId=" + orderId);
        }
        if (!admissionService.refund(requireActivity(order.getActivityId()), order)) {
            throw new IllegalStateException("Redis 库存退款失败, orderId=" + orderId);
        }
        store.markRefunded(order);
    }

    private GroupBuyOrderEntity requireOrder(String orderId) {
        GroupBuyOrderEntity order = store.findOrder(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在, orderId=" + orderId);
        }
        return order;
    }

    private GroupBuyActivityEntity requireActivity(Long activityId) {
        GroupBuyActivityEntity activity = store.findActivity(activityId);
        if (activity == null) {
            throw new IllegalStateException("活动不存在, activityId=" + activityId);
        }
        return activity;
    }
}
