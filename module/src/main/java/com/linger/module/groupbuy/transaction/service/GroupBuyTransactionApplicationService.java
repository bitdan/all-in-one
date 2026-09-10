package com.linger.module.groupbuy.transaction.service;

import com.linger.module.groupbuy.transaction.dto.CreateActivityRequest;
import com.linger.module.groupbuy.transaction.dto.CreateGroupRequest;
import com.linger.module.groupbuy.transaction.dto.PaymentCallbackRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderResponse;
import com.linger.module.groupbuy.transaction.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.transaction.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.transaction.exception.GroupBuyBusinessException;
import com.linger.module.groupbuy.transaction.model.ActivityStatus;
import com.linger.module.groupbuy.transaction.model.GroupInstanceStatus;
import com.linger.module.groupbuy.transaction.model.GroupOrderStatus;
import com.linger.module.groupbuy.transaction.model.ReservationResult;
import com.linger.module.redisson.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RateIntervalUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 拼团交易应用服务，负责串联 PostgreSQL 业务事实与 Redis 高并发准入状态。
 *
 * <p>跨存储不使用分布式事务，而是采用 Saga：数据库先保存 INIT 订单，Redis Lua 原子预占，
 * 随后数据库确认 WAIT_PAY；任一步失败都保留可识别状态，由释放操作和定时对账最终收敛。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupBuyTransactionApplicationService {

    private final GroupBuyTransactionStore store;
    private final RedisGroupBuyAdmissionService admissionService;
    private final RateLimiterService rateLimiterService;

    public GroupBuyActivityEntity createActivity(CreateActivityRequest request) {
        validateActivity(request);
        if (request.getPerUserLimit() == null) {
            request.setPerUserLimit(1);
        }
        if (request.getPayTimeoutSeconds() == null) {
            request.setPayTimeoutSeconds(900L);
        }
        if (request.getGroupTimeoutSeconds() == null) {
            request.setGroupTimeoutSeconds(86400L);
        }
        return store.createActivity(request);
    }

    public GroupBuyActivityEntity publishActivity(Long activityId) {
        GroupBuyActivityEntity activity = requireActivity(activityId);
        if (activity.getStatus() != ActivityStatus.READY && activity.getStatus() != ActivityStatus.RUNNING) {
            throw business("INVALID_ACTIVITY_STATE", "只有 READY 活动可以发布");
        }
        // Redis 初始化采用“已存在则不覆盖”，因此即使数据库状态提交失败，发布请求也可以安全重试。
        admissionService.initializeActivity(activity);
        GroupBuyActivityEntity running = store.markActivityRunning(activityId);
        if (running == null) {
            throw business("PUBLISH_CONFLICT", "活动状态已被其他请求修改");
        }
        return running;
    }

    public GroupBuyGroupEntity createGroup(Long activityId, CreateGroupRequest request) {
        if (request == null || request.getCreatorUserId() == null) {
            throw business("INVALID_REQUEST", "开团用户不能为空");
        }
        GroupBuyActivityEntity activity = requireActivity(activityId);
        ensureActivityRunning(activity, now());

        GroupBuyGroupEntity group = store.createGroup(activity, request.getCreatorUserId());
        admissionService.initializeGroup(activity, group);
        GroupBuyGroupEntity opened = store.openGroup(group);
        if (opened == null) {
            throw business("OPEN_GROUP_FAILED", "开团状态更新失败");
        }
        return opened;
    }

    public PlaceGroupOrderResponse placeOrder(PlaceGroupOrderRequest request) {
        validatePlaceOrder(request);
        // 限流位于数据库访问之前，避免同一用户的重复点击占满连接池。
        if (!rateLimiterService.tryAcquire("groupbuy:user:" + request.getActivityId() + ":" + request.getUserId(),
                5, 1, RateIntervalUnit.SECONDS)) {
            throw business("RATE_LIMITED", "操作过于频繁，请稍后再试");
        }

        // 第一层幂等：数据库唯一键 (user_id, request_id)；并发穿透时 insert 的唯一约束还会再次兜底。
        GroupBuyOrderEntity existing = store.findByUserRequest(request.getUserId(), request.getRequestId());
        if (existing != null) {
            return response("DUPLICATE_REQUEST", "重复请求，返回原订单", existing);
        }

        OffsetDateTime now = now();
        GroupBuyActivityEntity activity = requireActivity(request.getActivityId());
        ensureActivityRunning(activity, now);
        GroupBuyGroupEntity group = requireGroup(request.getGroupId());
        if (!activity.getId().equals(group.getActivityId())) {
            throw business("GROUP_ACTIVITY_MISMATCH", "团不属于指定活动");
        }
        if (group.getStatus() != GroupInstanceStatus.OPEN || !now.isBefore(group.getExpireAt())) {
            throw business("GROUP_NOT_OPEN", "团已关闭或已过期");
        }

        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        BigDecimal payableAmount = activity.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        String orderId = UUID.randomUUID().toString();
        GroupBuyOrderEntity order = GroupBuyOrderEntity.builder()
                .id(orderId)
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .activityId(activity.getId())
                .groupId(group.getId())
                .skuId(activity.getSkuId())
                .quantity(quantity)
                .unitPrice(activity.getUnitPrice())
                .discountAmount(BigDecimal.ZERO)
                .payableAmount(payableAmount)
                .status(GroupOrderStatus.INIT)
                .version(0L)
                .createdBy(request.getUserId())
                .updatedBy(request.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 先落 INIT 订单，为“Redis 成功后进程立即崩溃”的场景留下可对账的业务锚点。
        try {
            store.createInitOrder(order);
        } catch (DuplicateKeyException e) {
            GroupBuyOrderEntity duplicate = store.findByUserRequest(request.getUserId(), request.getRequestId());
            if (duplicate != null) {
                return response("DUPLICATE_REQUEST", "重复请求，返回原订单", duplicate);
            }
            throw e;
        }

        OffsetDateTime payDeadline = earlier(
                earlier(now.plusSeconds(activity.getPayTimeoutSeconds()), group.getExpireAt()),
                activity.getEndsAt());
        // Lua 在一个原子操作内完成库存、团名额、个人限购、重复参团和 Redis 幂等校验。
        ReservationResult reservation = admissionService.reserve(activity, group, order, payDeadline);
        if (!reservation.isAccepted()) {
            store.rejectInitOrder(orderId, reservation.getCode().name());
            GroupBuyOrderEntity rejected = store.findOrder(orderId);
            return response(reservation.getCode().name(), reservationMessage(reservation.getCode()), rejected);
        }
        if (reservation.getCode() == ReservationResult.Code.DUPLICATE
                && !orderId.equals(reservation.getOrderId())) {
            store.rejectInitOrder(orderId, "REDIS_DUPLICATE_REQUEST");
            GroupBuyOrderEntity duplicate = store.findOrder(reservation.getOrderId());
            if (duplicate != null) {
                return response("DUPLICATE_REQUEST", "重复请求，返回原订单", duplicate);
            }
            throw business("IDEMPOTENCY_CONFLICT", "幂等记录存在，但原订单尚未恢复");
        }

        // 数据库确认会在同一事务内写成员、库存流水和支付超时任务。
        try {
            store.confirmReservation(order, payDeadline);
        } catch (RuntimeException e) {
            // 数据库确认失败时立即做幂等释放；若进程在此之前退出，INIT 对账任务仍会完成释放。
            admissionService.release(activity, order);
            store.rejectInitOrder(orderId, "DATABASE_CONFIRM_FAILED");
            throw e;
        }
        return response("ACCEPTED", "库存和团名额预占成功", store.findOrder(orderId));
    }

    public String recordPayment(PaymentCallbackRequest request) {
        validatePayment(request);
        GroupBuyOrderEntity order = store.findOrder(request.getOrderId());
        if (order == null) {
            throw business("ORDER_NOT_FOUND", "订单不存在");
        }
        if (request.getPaidAmount().compareTo(order.getPayableAmount()) != 0) {
            throw business("PAYMENT_AMOUNT_MISMATCH", "支付金额与订单应付金额不一致");
        }
        OffsetDateTime paidAt = request.getPaidAt() == null ? now() : request.getPaidAt();
        // 状态 CAS、支付流水唯一约束和 Outbox 写入位于同一数据库事务中。
        GroupBuyTransactionStore.PaymentRecordResult result =
                store.recordPayment(request.getOrderId(), request.getPaymentNo(), paidAt);
        switch (result) {
            case SUCCESS:
                return "PAYMENT_ACCEPTED";
            case DUPLICATE:
                return "DUPLICATE_CALLBACK";
            case EXPIRED:
                throw business("ORDER_EXPIRED", "订单已超过支付截止时间");
            case GROUP_UNAVAILABLE:
                throw business("GROUP_UNAVAILABLE", "团已不可支付");
            case NOT_FOUND:
                throw business("ORDER_NOT_FOUND", "订单不存在");
            default:
                throw business("INVALID_ORDER_STATE", "当前订单状态不允许支付");
        }
    }

    public GroupBuyOrderEntity findOrder(String orderId) {
        GroupBuyOrderEntity order = store.findOrder(orderId);
        if (order == null) {
            throw business("ORDER_NOT_FOUND", "订单不存在");
        }
        return order;
    }

    private GroupBuyActivityEntity requireActivity(Long activityId) {
        GroupBuyActivityEntity activity = activityId == null ? null : store.findActivity(activityId);
        if (activity == null) {
            throw business("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        return activity;
    }

    private GroupBuyGroupEntity requireGroup(Long groupId) {
        GroupBuyGroupEntity group = groupId == null ? null : store.findGroup(groupId);
        if (group == null) {
            throw business("GROUP_NOT_FOUND", "团不存在");
        }
        return group;
    }

    private void ensureActivityRunning(GroupBuyActivityEntity activity, OffsetDateTime currentTime) {
        if (activity.getStatus() != ActivityStatus.RUNNING
                || currentTime.isBefore(activity.getStartsAt())
                || !currentTime.isBefore(activity.getEndsAt())) {
            throw business("ACTIVITY_NOT_RUNNING", "活动未开始、已结束或未发布");
        }
    }

    private void validateActivity(CreateActivityRequest request) {
        if (request == null || !StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getSkuId())) {
            throw business("INVALID_REQUEST", "活动名称和 SKU 不能为空");
        }
        if (request.getUnitPrice() == null || request.getUnitPrice().signum() < 0) {
            throw business("INVALID_PRICE", "活动价格不能为负数");
        }
        if (request.getTotalStock() == null || request.getTotalStock() < 0) {
            throw business("INVALID_STOCK", "库存不能为负数");
        }
        if (request.getTargetCount() == null || request.getTargetCount() <= 0) {
            throw business("INVALID_TARGET", "成团人数必须大于 0");
        }
        if (request.getStartsAt() == null || request.getEndsAt() == null
                || !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw business("INVALID_ACTIVITY_TIME", "活动结束时间必须晚于开始时间");
        }
        if (request.getPerUserLimit() != null && request.getPerUserLimit() <= 0) {
            throw business("INVALID_USER_LIMIT", "每人限购数量必须大于 0");
        }
    }

    private void validatePlaceOrder(PlaceGroupOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())
                || request.getUserId() == null || request.getActivityId() == null || request.getGroupId() == null) {
            throw business("INVALID_REQUEST", "requestId、userId、activityId 和 groupId 不能为空");
        }
        if (request.getQuantity() != null && request.getQuantity() <= 0) {
            throw business("INVALID_QUANTITY", "购买数量必须大于 0");
        }
    }

    private void validatePayment(PaymentCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderId())
                || !StringUtils.hasText(request.getPaymentNo()) || request.getPaidAmount() == null) {
            throw business("INVALID_PAYMENT_CALLBACK", "订单号、支付流水号和支付金额不能为空");
        }
    }

    private PlaceGroupOrderResponse response(String code, String message, GroupBuyOrderEntity order) {
        return new PlaceGroupOrderResponse(code, message, order.getId(), order.getStatus(),
                order.getPayableAmount(), order.getPayDeadline());
    }

    private String reservationMessage(ReservationResult.Code code) {
        switch (code) {
            case OUT_OF_STOCK:
                return "库存不足";
            case GROUP_FULL:
                return "团名额已满";
            case ALREADY_JOINED:
                return "用户已参加该团";
            case USER_LIMIT_REACHED:
                return "超过个人限购数量";
            case ACTIVITY_NOT_RUNNING:
                return "活动不在可下单时间";
            case GROUP_NOT_OPEN:
                return "团已关闭或已过期";
            default:
                return "预占失败";
        }
    }

    private GroupBuyBusinessException business(String code, String message) {
        return new GroupBuyBusinessException(code, message);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime earlier(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }
}
