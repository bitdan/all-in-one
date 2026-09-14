package com.linger.module.groupbuy.transaction.service;

import com.linger.module.groupbuy.transaction.dto.CreateActivityRequest;
import com.linger.module.groupbuy.activity.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.infrastructure.entity.GroupBuyDelayTaskEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryReservationEntity;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryStockEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyMemberEntity;
import com.linger.module.groupbuy.order.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.infrastructure.entity.GroupBuyOutboxEventEntity;
import com.linger.module.groupbuy.activity.mapper.GroupBuyActivityMapper;
import com.linger.module.groupbuy.infrastructure.mapper.GroupBuyDelayTaskMapper;
import com.linger.module.groupbuy.group.mapper.GroupBuyGroupMapper;
import com.linger.module.groupbuy.inventory.mapper.GroupBuyInventoryLedgerMapper;
import com.linger.module.groupbuy.inventory.mapper.GroupBuyInventoryReservationMapper;
import com.linger.module.groupbuy.inventory.mapper.GroupBuyInventoryStockMapper;
import com.linger.module.groupbuy.group.mapper.GroupBuyMemberMapper;
import com.linger.module.groupbuy.order.mapper.GroupBuyOrderMapper;
import com.linger.module.groupbuy.infrastructure.mapper.GroupBuyOutboxEventMapper;
import com.linger.module.groupbuy.activity.model.ActivityStatus;
import com.linger.module.groupbuy.infrastructure.model.GroupBuyDelayTaskType;
import com.linger.module.groupbuy.infrastructure.model.GroupBuyEventType;
import com.linger.module.groupbuy.group.model.GroupInstanceStatus;
import com.linger.module.groupbuy.group.model.GroupMemberStatus;
import com.linger.module.groupbuy.order.model.GroupOrderStatus;
import com.linger.module.groupbuy.inventory.model.InventoryReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 拼团数据库事务边界。
 *
 * <p>BaseMapper 负责普通实体读写；所有状态转换均调用带旧状态条件的专用 SQL，避免“先查后改”
 * 在并发支付、取消和超时之间产生覆盖。Outbox 与业务状态始终在同一事务内提交。</p>
 */
@Component
@RequiredArgsConstructor
public class GroupBuyTransactionStore {

    private final GroupBuyActivityMapper activityMapper;
    private final GroupBuyGroupMapper groupMapper;
    private final GroupBuyOrderMapper orderMapper;
    private final GroupBuyMemberMapper memberMapper;
    private final GroupBuyInventoryStockMapper inventoryStockMapper;
    private final GroupBuyInventoryReservationMapper inventoryReservationMapper;
    private final GroupBuyInventoryLedgerMapper ledgerMapper;
    private final GroupBuyOutboxEventMapper outboxMapper;
    private final GroupBuyDelayTaskMapper delayTaskMapper;

    @Transactional
    public GroupBuyActivityEntity createActivity(CreateActivityRequest request) {
        OffsetDateTime now = now();
        GroupBuyActivityEntity entity = GroupBuyActivityEntity.builder()
                .name(request.getName())
                .skuId(request.getSkuId())
                .unitPrice(request.getUnitPrice())
                .totalStock(request.getTotalStock())
                .perUserLimit(request.getPerUserLimit())
                .targetCount(request.getTargetCount())
                .payTimeoutSeconds(request.getPayTimeoutSeconds())
                .groupTimeoutSeconds(request.getGroupTimeoutSeconds())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .status(ActivityStatus.READY)
                .version(0L)
                .createdBy(0L)
                .updatedBy(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        activityMapper.insert(entity);
        inventoryStockMapper.insert(GroupBuyInventoryStockEntity.builder()
                .activityId(entity.getId())
                .skuId(entity.getSkuId())
                .totalQuantity(entity.getTotalStock())
                .availableQuantity(entity.getTotalStock())
                .reservedQuantity(0)
                .confirmedQuantity(0)
                .version(0L)
                .createdBy(0L)
                .updatedBy(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return entity;
    }

    @Transactional
    public GroupBuyActivityEntity markActivityRunning(Long activityId) {
        GroupBuyActivityEntity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            return null;
        }
        if (activity.getStatus() == ActivityStatus.RUNNING) {
            return activity;
        }
        if (activityMapper.markRunning(activityId) != 1) {
            return null;
        }
        return activityMapper.selectById(activityId);
    }

    public GroupBuyActivityEntity findActivity(Long activityId) {
        return activityMapper.selectById(activityId);
    }

    public GroupBuyGroupEntity createGroup(GroupBuyActivityEntity activity, Long creatorUserId) {
        OffsetDateTime now = now();
        GroupBuyGroupEntity group = GroupBuyGroupEntity.builder()
                .activityId(activity.getId())
                .creatorUserId(creatorUserId)
                .targetCount(activity.getTargetCount())
                .reservedCount(0)
                .paidCount(0)
                .status(GroupInstanceStatus.INIT)
                .expireAt(earlier(now.plusSeconds(activity.getGroupTimeoutSeconds()), activity.getEndsAt()))
                .version(0L)
                .createdBy(creatorUserId)
                .updatedBy(creatorUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        groupMapper.insert(group);
        return group;
    }

    @Transactional
    public GroupBuyGroupEntity openGroup(GroupBuyGroupEntity group) {
        if (groupMapper.markOpen(group.getId()) != 1) {
            GroupBuyGroupEntity current = groupMapper.selectById(group.getId());
            return current != null && current.getStatus() == GroupInstanceStatus.OPEN ? current : null;
        }
        delayTaskMapper.insertIgnore(GroupBuyDelayTaskType.GROUP_TIMEOUT,
                String.valueOf(group.getId()), group.getExpireAt());
        return groupMapper.selectById(group.getId());
    }

    public GroupBuyGroupEntity findGroup(Long groupId) {
        return groupMapper.selectById(groupId);
    }

    public GroupBuyOrderEntity findOrder(String orderId) {
        return orderMapper.selectById(orderId);
    }

    public GroupBuyOrderEntity findByUserRequest(Long userId, String requestId) {
        return orderMapper.selectByUserRequest(userId, requestId);
    }

    public void createInitOrder(GroupBuyOrderEntity order) {
        orderMapper.insert(order);
    }

    @Transactional
    public void confirmReservation(GroupBuyOrderEntity order, OffsetDateTime payDeadline) {
        // 订单、数据库库存账、预占单、成员、流水与超时任务必须在同一事务内提交。
        if (orderMapper.markWaitPay(order.getId(), order.getId(), payDeadline) != 1) {
            throw new IllegalStateException("订单不在 INIT 状态, orderId=" + order.getId());
        }
        OffsetDateTime now = now();
        inventoryReservationMapper.insert(GroupBuyInventoryReservationEntity.builder()
                .id(order.getId())
                .orderId(order.getId())
                .activityId(order.getActivityId())
                .skuId(order.getSkuId())
                .quantity(order.getQuantity())
                .status(InventoryReservationStatus.RESERVED)
                .expireAt(payDeadline)
                .version(0L)
                .createdBy(order.getUserId())
                .updatedBy(order.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build());
        memberMapper.insert(GroupBuyMemberEntity.builder()
                .groupId(order.getGroupId())
                .userId(order.getUserId())
                .orderId(order.getId())
                .status(GroupMemberStatus.RESERVED)
                .createdBy(order.getUserId())
                .updatedBy(order.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build());
        ledgerMapper.insertIgnore(order.getActivityId(), order.getSkuId(), order.getId(),
                "RESERVE", order.getQuantity());
        delayTaskMapper.insertIgnore(GroupBuyDelayTaskType.PAYMENT_TIMEOUT, order.getId(), payDeadline);

        // 热点计数放在明细写入之后，按“团 -> 库存”加锁，与支付、取消和退款保持一致。
        // 多个团共享同一库存行，因此库存必须最后更新，避免持有库存锁时写明细或等待团锁。
        if (groupMapper.incrementReserved(order.getGroupId()) != 1) {
            throw new IllegalStateException("数据库团名额镜像更新失败, groupId=" + order.getGroupId());
        }
        if (inventoryStockMapper.reserve(order.getActivityId(), order.getSkuId(), order.getQuantity()) != 1) {
            // 条件扣减失败仍抛出异常，由同一事务回滚此前的订单、明细和团计数。
            throw new IllegalStateException("数据库可用库存不足, orderId=" + order.getId());
        }
    }

    public boolean rejectInitOrder(String orderId, String reason) {
        return orderMapper.markRejected(orderId, abbreviate(reason, 128)) == 1;
    }

    @Transactional
    public PaymentRecordResult recordPayment(String orderId, String paymentNo, OffsetDateTime paidAt) {
        GroupBuyOrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            return PaymentRecordResult.NOT_FOUND;
        }
        if (order.getStatus() == GroupOrderStatus.PAID
                || order.getStatus() == GroupOrderStatus.GROUP_SUCCESS
                || order.getStatus() == GroupOrderStatus.COMPLETED) {
            return paymentNo.equals(order.getPaymentNo())
                    ? PaymentRecordResult.DUPLICATE : PaymentRecordResult.INVALID_STATE;
        }
        GroupBuyGroupEntity group = groupMapper.selectForUpdate(order.getGroupId());
        if (group == null || group.getStatus() != GroupInstanceStatus.OPEN) {
            return PaymentRecordResult.GROUP_UNAVAILABLE;
        }
        if (order.getPayDeadline() != null && paidAt.isAfter(order.getPayDeadline())) {
            return PaymentRecordResult.EXPIRED;
        }
        // WAIT_PAY -> PAID 是 CAS；与超时取消并发时只能有一个更新成功。
        if (orderMapper.markPaid(orderId, paymentNo, paidAt) != 1) {
            return PaymentRecordResult.INVALID_STATE;
        }
        insertOutbox(GroupBuyEventType.ORDER_PAID, "ORDER", orderId);
        return PaymentRecordResult.SUCCESS;
    }

    @Transactional
    public SettlementResult applyPaidOrder(String orderId) {
        GroupBuyOrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在, orderId=" + orderId);
        }
        // 同一个团的支付结算串行持有数据库行锁，防止多个“最后一人”重复把团推进成功。
        GroupBuyGroupEntity group = groupMapper.selectForUpdate(order.getGroupId());
        if (group == null) {
            throw new IllegalStateException("团不存在, groupId=" + order.getGroupId());
        }
        if (group.getStatus() == GroupInstanceStatus.SUCCESS) {
            return new SettlementResult(true, false);
        }
        if (group.getStatus() != GroupInstanceStatus.OPEN) {
            if (orderMapper.markRefunding(orderId) == 1) {
                memberMapper.markRefunding(orderId);
                insertOutbox(GroupBuyEventType.ORDER_REFUND, "ORDER", orderId);
            }
            return new SettlementResult(false, true);
        }

        // 成员状态 CAS 是 Outbox 至少一次消费的幂等屏障。
        if (memberMapper.markPaid(orderId) == 0) {
            return new SettlementResult(false, false);
        }
        if (inventoryReservationMapper.markConfirmed(orderId) != 1
                || inventoryStockMapper.confirm(order.getActivityId(), order.getSkuId(), order.getQuantity()) != 1) {
            throw new IllegalStateException("确认数据库库存失败, orderId=" + orderId);
        }
        if (groupMapper.incrementPaid(group.getId()) != 1) {
            throw new IllegalStateException("增加已支付人数失败, groupId=" + group.getId());
        }
        ledgerMapper.insertIgnore(order.getActivityId(), order.getSkuId(), orderId,
                "CONFIRM", order.getQuantity());

        GroupBuyGroupEntity updated = groupMapper.selectById(group.getId());
        if (updated.getPaidCount() >= updated.getTargetCount() && groupMapper.markSuccess(group.getId()) == 1) {
            memberMapper.markConfirmedByGroup(group.getId());
            orderMapper.markGroupSuccess(group.getId());
            insertOutbox(GroupBuyEventType.GROUP_SUCCESS, "GROUP", String.valueOf(group.getId()));
            return new SettlementResult(true, false);
        }
        return new SettlementResult(false, false);
    }

    @Transactional
    public boolean cancelUnpaidOrder(String orderId) {
        GroupBuyOrderEntity order = orderMapper.selectById(orderId);
        if (order == null || orderMapper.cancelUnpaid(orderId) != 1) {
            return false;
        }
        if (memberMapper.cancelReservation(orderId) != 1
                || groupMapper.decrementReserved(order.getGroupId()) != 1) {
            throw new IllegalStateException("释放数据库团名额失败, orderId=" + orderId);
        }
        releaseReservedInventory(order);
        insertOutbox(GroupBuyEventType.ORDER_RELEASE, "ORDER", orderId);
        return true;
    }

    @Transactional
    public boolean rejectStaleInit(String orderId) {
        GroupBuyOrderEntity order = orderMapper.selectById(orderId);
        if (order == null || orderMapper.markRejected(orderId, "RESERVATION_RECOVERY") != 1) {
            return false;
        }
        insertOutbox(GroupBuyEventType.ORDER_RELEASE, "ORDER", orderId);
        return true;
    }

    @Transactional
    public boolean failExpiredGroup(Long groupId) {
        // 只有 OPEN 且数据库时间已到期的团能失败；与最后一笔支付通过同一团记录竞争行锁。
        if (groupMapper.markFailedIfExpired(groupId) != 1) {
            return false;
        }
        List<GroupBuyOrderEntity> orders = orderMapper.selectByGroupId(groupId);
        for (GroupBuyOrderEntity order : orders) {
            if (order.getStatus() == GroupOrderStatus.WAIT_PAY && orderMapper.cancelUnpaid(order.getId()) == 1) {
                if (memberMapper.cancelReservation(order.getId()) != 1
                        || groupMapper.decrementReserved(order.getGroupId()) != 1) {
                    throw new IllegalStateException("释放数据库团名额失败, orderId=" + order.getId());
                }
                releaseReservedInventory(order);
                insertOutbox(GroupBuyEventType.ORDER_RELEASE, "ORDER", order.getId());
            } else if (order.getStatus() == GroupOrderStatus.PAID && orderMapper.markRefunding(order.getId()) == 1) {
                memberMapper.markRefunding(order.getId());
                insertOutbox(GroupBuyEventType.ORDER_REFUND, "ORDER", order.getId());
            }
        }
        return true;
    }

    @Transactional
    public boolean markRefunded(GroupBuyOrderEntity order) {
        if (orderMapper.markRefunded(order.getId()) != 1) {
            GroupBuyOrderEntity current = orderMapper.selectById(order.getId());
            return current != null && current.getStatus() == GroupOrderStatus.REFUNDED;
        }
        if (memberMapper.markRefunded(order.getId()) != 1) {
            throw new IllegalStateException("更新团成员退款状态失败, orderId=" + order.getId());
        }
        // refundInventory 会更新库存；先锁团，避免与下单/取消的“团 -> 库存”顺序相反。
        if (groupMapper.selectForUpdate(order.getGroupId()) == null) {
            throw new IllegalStateException("退款所属团不存在, groupId=" + order.getGroupId());
        }
        InventoryReservationStatus previousInventoryStatus = refundInventory(order);
        int affectedGroupRows = previousInventoryStatus == InventoryReservationStatus.CONFIRMED
                ? groupMapper.decrementPaidAndReserved(order.getGroupId())
                : groupMapper.decrementReserved(order.getGroupId());
        if (affectedGroupRows != 1) {
            throw new IllegalStateException("回退数据库团计数失败, orderId=" + order.getId());
        }
        ledgerMapper.insertIgnore(order.getActivityId(), order.getSkuId(), order.getId(),
                "REFUND", order.getQuantity());
        return true;
    }

    @Transactional
    public boolean movePaidOrderToRefunding(String orderId) {
        if (orderMapper.markRefunding(orderId) != 1) {
            GroupBuyOrderEntity current = orderMapper.selectById(orderId);
            return current != null && (current.getStatus() == GroupOrderStatus.REFUNDING
                    || current.getStatus() == GroupOrderStatus.REFUNDED);
        }
        memberMapper.markRefunding(orderId);
        insertOutbox(GroupBuyEventType.ORDER_REFUND, "ORDER", orderId);
        return true;
    }

    public List<GroupBuyOrderEntity> findStaleInitOrders(OffsetDateTime before, int limit) {
        return orderMapper.selectStaleInit(before, limit);
    }

    public List<GroupBuyOrderEntity> findExpiredUnpaidOrders(int limit) {
        return orderMapper.selectExpiredUnpaid(limit);
    }

    public List<GroupBuyOutboxEventEntity> claimOutbox(String workerId, int limit) {
        // Mapper 使用 FOR UPDATE SKIP LOCKED，多节点可并行抢占且不会重复持有同一批事件。
        List<GroupBuyOutboxEventEntity> result = outboxMapper.claimBatch(workerId, limit);
        return result == null ? Collections.emptyList() : result;
    }

    public void markOutboxDone(String eventId, String workerId) {
        outboxMapper.markDone(eventId, workerId);
    }

    public void markOutboxFailed(String eventId, String workerId, int retryCount, Throwable error) {
        outboxMapper.markFailed(eventId, workerId, 8, retryDelaySeconds(retryCount),
                abbreviate(error.getMessage(), 500));
    }

    public List<GroupBuyDelayTaskEntity> claimDelayTasks(String workerId, int limit,
                                                          long loadAheadSeconds, long lockSeconds) {
        List<GroupBuyDelayTaskEntity> result = delayTaskMapper.claimBatch(
                workerId, limit, loadAheadSeconds, lockSeconds);
        return result == null ? Collections.emptyList() : result;
    }

    public boolean markDelayTaskRunning(Long id, String workerId) {
        return delayTaskMapper.markRunning(id, workerId) == 1;
    }

    public void markDelayTaskDone(Long id, String workerId) {
        delayTaskMapper.markDone(id, workerId);
    }

    public void markDelayTaskFailed(Long id, String workerId, int retryCount, Throwable error) {
        delayTaskMapper.markFailed(id, workerId, 8, retryDelaySeconds(retryCount),
                abbreviate(error.getMessage(), 500));
    }

    private void insertOutbox(String eventType, String aggregateType, String aggregateId) {
        outboxMapper.insertEvent(UUID.randomUUID().toString(), eventType, aggregateType, aggregateId, "{}");
    }

    private void releaseReservedInventory(GroupBuyOrderEntity order) {
        if (inventoryReservationMapper.markReleased(order.getId()) != 1
                || inventoryStockMapper.release(order.getActivityId(), order.getSkuId(), order.getQuantity()) != 1) {
            throw new IllegalStateException("释放数据库预占库存失败, orderId=" + order.getId());
        }
        ledgerMapper.insertIgnore(order.getActivityId(), order.getSkuId(), order.getId(),
                "RELEASE", order.getQuantity());
    }

    private InventoryReservationStatus refundInventory(GroupBuyOrderEntity order) {
        GroupBuyInventoryReservationEntity reservation =
                inventoryReservationMapper.selectByOrderId(order.getId());
        if (reservation == null) {
            throw new IllegalStateException("库存预占单不存在, orderId=" + order.getId());
        }
        InventoryReservationStatus previousStatus = reservation.getStatus();
        if (previousStatus == InventoryReservationStatus.REFUNDED) {
            return previousStatus;
        }
        if (previousStatus != InventoryReservationStatus.RESERVED
                && previousStatus != InventoryReservationStatus.CONFIRMED) {
            throw new IllegalStateException("库存预占单状态不可退款, orderId=" + order.getId()
                    + ", status=" + previousStatus);
        }
        if (inventoryReservationMapper.markRefunded(order.getId(), previousStatus.name()) != 1) {
            throw new IllegalStateException("库存预占单退款状态更新失败, orderId=" + order.getId());
        }
        int affected = previousStatus == InventoryReservationStatus.CONFIRMED
                ? inventoryStockMapper.refund(order.getActivityId(), order.getSkuId(), order.getQuantity())
                : inventoryStockMapper.release(order.getActivityId(), order.getSkuId(), order.getQuantity());
        if (affected != 1) {
            throw new IllegalStateException("返还数据库库存失败, orderId=" + order.getId());
        }
        return previousStatus;
    }

    private long retryDelaySeconds(int retryCount) {
        long[] delays = {1L, 5L, 30L, 120L, 600L, 1800L, 3600L, 7200L};
        return delays[Math.min(Math.max(retryCount, 0), delays.length - 1)];
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime earlier(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    public enum PaymentRecordResult {
        SUCCESS,
        DUPLICATE,
        NOT_FOUND,
        EXPIRED,
        GROUP_UNAVAILABLE,
        INVALID_STATE
    }

    @Data
    @AllArgsConstructor
    public static class SettlementResult {
        private boolean groupSuccess;
        private boolean refundRequired;
    }
}
