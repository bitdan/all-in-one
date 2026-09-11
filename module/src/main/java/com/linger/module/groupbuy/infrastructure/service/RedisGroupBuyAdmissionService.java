package com.linger.module.groupbuy.infrastructure.service;

import com.linger.module.groupbuy.activity.entity.GroupBuyActivityEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.order.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.transaction.model.ReservationResult;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Redis 高并发准入层。
 *
 * <p>同一活动 SKU 的库存、团、成员、用户计数和预占记录共享 Redis Cluster hash tag，
 * 因而可以在一个 Lua 脚本中原子修改。脚本返回业务码，主链路不使用分布式锁串行扣库存。</p>
 */
@Service
@RequiredArgsConstructor
public class RedisGroupBuyAdmissionService {

    /** 活动发布脚本：仅在库存 key 不存在时初始化，重试不会覆盖已经售出的库存。 */
    private static final String INIT_ACTIVITY_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end " +
            "redis.call('HSET', KEYS[1], 'status', 'RUNNING', 'available', ARGV[1], " +
            "'reserved', 0, 'confirmed', 0, 'perUserLimit', ARGV[2], " +
            "'startsAt', ARGV[3], 'endsAt', ARGV[4]); return 1";

    /** 开团脚本：初始化团容量和截止时间，重复调用不重置已占名额。 */
    private static final String INIT_GROUP_SCRIPT =
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end " +
            "redis.call('HSET', KEYS[1], 'status', 'OPEN', 'targetCount', ARGV[1], " +
            "'reservedCount', 0, 'paidCount', 0, 'expireAt', ARGV[2]); return 1";

    /**
     * 下单预占脚本：在一次原子操作中完成双重幂等、时间窗口、库存、团容量、重复参团和个人限购校验，
     * 成功后同时写入预占 Hash 与截止时间 ZSet，供取消、支付确认及故障补偿使用。
     */
    private static final String RESERVE_SCRIPT =
            "local oldOrder = redis.call('GET', KEYS[6]); " +
            "if oldOrder then return {2, oldOrder} end; " +
            "if redis.call('HGET', KEYS[1], 'status') ~= 'RUNNING' then return {-4, ''} end; " +
            "local now = tonumber(ARGV[6]); " +
            "local startsAt = tonumber(redis.call('HGET', KEYS[1], 'startsAt') or '0'); " +
            "local endsAt = tonumber(redis.call('HGET', KEYS[1], 'endsAt') or '0'); " +
            "if now < startsAt or now >= endsAt then return {-4, ''} end; " +
            "if redis.call('HGET', KEYS[2], 'status') ~= 'OPEN' then return {-5, ''} end; " +
            "local expireAt = tonumber(redis.call('HGET', KEYS[2], 'expireAt') or '0'); " +
            "if now >= expireAt then return {-5, ''} end; " +
            "if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then return {-3, ''} end; " +
            "local quantity = tonumber(ARGV[3]); " +
            "local userCount = tonumber(redis.call('GET', KEYS[7]) or '0'); " +
            "local userLimit = tonumber(redis.call('HGET', KEYS[1], 'perUserLimit') or '1'); " +
            "if userCount + quantity > userLimit then return {-6, ''} end; " +
            "local available = tonumber(redis.call('HGET', KEYS[1], 'available') or '0'); " +
            "if available < quantity then return {-1, ''} end; " +
            "local reservedCount = tonumber(redis.call('HGET', KEYS[2], 'reservedCount') or '0'); " +
            "local targetCount = tonumber(redis.call('HGET', KEYS[2], 'targetCount') or '0'); " +
            "if reservedCount >= targetCount then return {-2, ''} end; " +
            "redis.call('HINCRBY', KEYS[1], 'available', -quantity); " +
            "redis.call('HINCRBY', KEYS[1], 'reserved', quantity); " +
            "redis.call('HINCRBY', KEYS[2], 'reservedCount', 1); " +
            "redis.call('SADD', KEYS[3], ARGV[2]); " +
            "redis.call('INCRBY', KEYS[7], quantity); " +
            "redis.call('HSET', KEYS[4], 'orderId', ARGV[1], 'userId', ARGV[2], " +
            "'quantity', quantity, 'state', 'RESERVED', 'deadline', ARGV[4]); " +
            "redis.call('ZADD', KEYS[5], ARGV[4], ARGV[1]); " +
            "redis.call('SET', KEYS[6], ARGV[1], 'EX', ARGV[5]); " +
            "return {1, ARGV[1]}";

    /** 未支付释放脚本：仅 RESERVED 状态能释放，重复执行返回幂等成功。 */
    private static final String RELEASE_SCRIPT =
            "if redis.call('EXISTS', KEYS[4]) == 0 then return 0 end; " +
            "if redis.call('HGET', KEYS[4], 'state') ~= 'RESERVED' then return 2 end; " +
            "local quantity = tonumber(redis.call('HGET', KEYS[4], 'quantity') or '0'); " +
            "redis.call('HINCRBY', KEYS[1], 'available', quantity); " +
            "redis.call('HINCRBY', KEYS[1], 'reserved', -quantity); " +
            "redis.call('HINCRBY', KEYS[2], 'reservedCount', -1); " +
            "redis.call('SREM', KEYS[3], ARGV[2]); " +
            "redis.call('DECRBY', KEYS[6], quantity); " +
            "redis.call('ZREM', KEYS[5], ARGV[1]); " +
            "redis.call('HSET', KEYS[4], 'state', 'RELEASED'); return 1";

    /** 支付确认脚本：把 reserved 库存转为 confirmed，并由最后一名支付者原子推进 Redis 团成功。 */
    private static final String CONFIRM_PAYMENT_SCRIPT =
            "if redis.call('EXISTS', KEYS[3]) == 0 then return 0 end; " +
            "local state = redis.call('HGET', KEYS[3], 'state'); " +
            "if state == 'PAID' then return 3 end; " +
            "if state ~= 'RESERVED' then return 0 end; " +
            "if redis.call('HGET', KEYS[2], 'status') ~= 'OPEN' then return -1 end; " +
            "local expireAt = tonumber(redis.call('HGET', KEYS[2], 'expireAt') or '0'); " +
            "if tonumber(ARGV[2]) >= expireAt then return -2 end; " +
            "local quantity = tonumber(redis.call('HGET', KEYS[3], 'quantity') or '0'); " +
            "redis.call('HINCRBY', KEYS[1], 'reserved', -quantity); " +
            "redis.call('HINCRBY', KEYS[1], 'confirmed', quantity); " +
            "local paidCount = redis.call('HINCRBY', KEYS[2], 'paidCount', 1); " +
            "redis.call('HSET', KEYS[3], 'state', 'PAID'); " +
            "redis.call('ZREM', KEYS[4], ARGV[1]); " +
            "local targetCount = tonumber(redis.call('HGET', KEYS[2], 'targetCount') or '0'); " +
            "if paidCount >= targetCount then redis.call('HSET', KEYS[2], 'status', 'SUCCESS'); return 2 end; " +
            "return 1";

    /** 退款脚本：根据 RESERVED/PAID 来源把库存退回 available，状态保证只返还一次。 */
    private static final String REFUND_SCRIPT =
            "if redis.call('EXISTS', KEYS[4]) == 0 then return 0 end; " +
            "local state = redis.call('HGET', KEYS[4], 'state'); " +
            "if state == 'REFUNDED' or state == 'RELEASED' then return 2 end; " +
            "local quantity = tonumber(redis.call('HGET', KEYS[4], 'quantity') or '0'); " +
            "if state == 'PAID' then " +
            " redis.call('HINCRBY', KEYS[1], 'confirmed', -quantity); " +
            " redis.call('HINCRBY', KEYS[2], 'paidCount', -1); " +
            "else redis.call('HINCRBY', KEYS[1], 'reserved', -quantity); end; " +
            "redis.call('HINCRBY', KEYS[1], 'available', quantity); " +
            "redis.call('HINCRBY', KEYS[2], 'reservedCount', -1); " +
            "redis.call('SREM', KEYS[3], ARGV[2]); " +
            "redis.call('DECRBY', KEYS[5], quantity); " +
            "redis.call('HSET', KEYS[4], 'state', 'REFUNDED'); return 1";

    /** 团超时脚本：只允许 OPEN -> FAILED，和最后一笔支付在 Redis 内竞争同一状态。 */
    private static final String FAIL_GROUP_SCRIPT =
            "local state = redis.call('HGET', KEYS[1], 'status'); " +
            "if state == 'FAILED' then return 2 end; " +
            "if state ~= 'OPEN' then return 0 end; " +
            "redis.call('HSET', KEYS[1], 'status', 'FAILED'); return 1";

    private final RedissonClient redissonClient;

    public void initializeActivity(GroupBuyActivityEntity activity) {
        evalInteger(INIT_ACTIVITY_SCRIPT,
                Collections.singletonList(stockKey(activity.getId(), activity.getSkuId())),
                activity.getTotalStock(), activity.getPerUserLimit(),
                toEpochMilli(activity.getStartsAt()), toEpochMilli(activity.getEndsAt()));
    }

    public void initializeGroup(GroupBuyActivityEntity activity, GroupBuyGroupEntity group) {
        evalInteger(INIT_GROUP_SCRIPT,
                Collections.singletonList(groupKey(activity.getId(), activity.getSkuId(), group.getId())),
                group.getTargetCount(), toEpochMilli(group.getExpireAt()));
    }

    @SuppressWarnings("unchecked")
    public ReservationResult reserve(GroupBuyActivityEntity activity,
                                     GroupBuyGroupEntity group,
                                     GroupBuyOrderEntity order,
                                     OffsetDateTime deadline) {
        // 所有 KEYS 都带相同 {activityId:skuId}，为未来切换 Redis Cluster 保留脚本兼容性。
        String prefix = prefix(activity.getId(), activity.getSkuId());
        List<Object> result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                RESERVE_SCRIPT,
                RScript.ReturnType.MULTI,
                Arrays.asList(
                        prefix + "stock",
                        prefix + "group:" + group.getId(),
                        prefix + "participants:" + group.getId(),
                        prefix + "reservation:" + order.getId(),
                        prefix + "reservation-deadlines",
                        prefix + "idem:" + order.getUserId() + ":" + order.getRequestId(),
                        prefix + "user-count:" + order.getUserId()),
                order.getId(), order.getUserId(), order.getQuantity(), toEpochMilli(deadline),
                Math.max(activity.getPayTimeoutSeconds() * 4, 3600L),
                System.currentTimeMillis());

        long code = Long.parseLong(String.valueOf(result.get(0)));
        String effectiveOrderId = result.size() > 1 ? String.valueOf(result.get(1)) : order.getId();
        return new ReservationResult(mapReservationCode(code), effectiveOrderId);
    }

    public boolean release(GroupBuyActivityEntity activity, GroupBuyOrderEntity order) {
        String prefix = prefix(activity.getId(), activity.getSkuId());
        long result = evalInteger(RELEASE_SCRIPT,
                Arrays.asList(
                        prefix + "stock",
                        prefix + "group:" + order.getGroupId(),
                        prefix + "participants:" + order.getGroupId(),
                        prefix + "reservation:" + order.getId(),
                        prefix + "reservation-deadlines",
                        prefix + "user-count:" + order.getUserId()),
                order.getId(), order.getUserId());
        return result == 0L || result == 1L || result == 2L;
    }

    public PaymentConfirmation confirmPayment(GroupBuyActivityEntity activity, GroupBuyOrderEntity order) {
        String prefix = prefix(activity.getId(), activity.getSkuId());
        long result = evalInteger(CONFIRM_PAYMENT_SCRIPT,
                Arrays.asList(
                        prefix + "stock",
                        prefix + "group:" + order.getGroupId(),
                        prefix + "reservation:" + order.getId(),
                        prefix + "reservation-deadlines"),
                order.getId(), System.currentTimeMillis());
        if (result == 2L) {
            return PaymentConfirmation.GROUP_SUCCESS;
        }
        if (result == 1L || result == 3L) {
            return PaymentConfirmation.CONFIRMED;
        }
        if (result == -1L || result == -2L) {
            return PaymentConfirmation.GROUP_UNAVAILABLE;
        }
        return PaymentConfirmation.RESERVATION_NOT_FOUND;
    }

    public boolean refund(GroupBuyActivityEntity activity, GroupBuyOrderEntity order) {
        String prefix = prefix(activity.getId(), activity.getSkuId());
        long result = evalInteger(REFUND_SCRIPT,
                Arrays.asList(
                        prefix + "stock",
                        prefix + "group:" + order.getGroupId(),
                        prefix + "participants:" + order.getGroupId(),
                        prefix + "reservation:" + order.getId(),
                        prefix + "user-count:" + order.getUserId()),
                order.getId(), order.getUserId());
        return result == 1L || result == 2L;
    }

    public boolean failGroup(GroupBuyActivityEntity activity, GroupBuyGroupEntity group) {
        long result = evalInteger(FAIL_GROUP_SCRIPT,
                Collections.singletonList(groupKey(activity.getId(), activity.getSkuId(), group.getId())));
        return result == 1L || result == 2L;
    }

    private long evalInteger(String script, List<Object> keys, Object... args) {
        Number result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER, keys, args);
        return result == null ? 0L : result.longValue();
    }

    private ReservationResult.Code mapReservationCode(long code) {
        switch ((int) code) {
            case 1:
                return ReservationResult.Code.SUCCESS;
            case 2:
                return ReservationResult.Code.DUPLICATE;
            case -1:
                return ReservationResult.Code.OUT_OF_STOCK;
            case -2:
                return ReservationResult.Code.GROUP_FULL;
            case -3:
                return ReservationResult.Code.ALREADY_JOINED;
            case -4:
                return ReservationResult.Code.ACTIVITY_NOT_RUNNING;
            case -5:
                return ReservationResult.Code.GROUP_NOT_OPEN;
            case -6:
                return ReservationResult.Code.USER_LIMIT_REACHED;
            default:
                return ReservationResult.Code.RESERVATION_NOT_FOUND;
        }
    }

    private String stockKey(Long activityId, String skuId) {
        return prefix(activityId, skuId) + "stock";
    }

    private String groupKey(Long activityId, String skuId, Long groupId) {
        return prefix(activityId, skuId) + "group:" + groupId;
    }

    private String prefix(Long activityId, String skuId) {
        return "groupbuy:{" + activityId + ":" + skuId + "}:";
    }

    private long toEpochMilli(OffsetDateTime value) {
        return value.toInstant().toEpochMilli();
    }

    public enum PaymentConfirmation {
        CONFIRMED,
        GROUP_SUCCESS,
        GROUP_UNAVAILABLE,
        RESERVATION_NOT_FOUND
    }
}
