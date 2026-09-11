package com.linger.module.groupbuy.order.model;

public enum GroupOrderStatus {
    /** 订单已落库，尚未完成 Redis 库存和团名额预占。 */
    INIT,
    /** 预占成功，等待用户支付。 */
    WAIT_PAY,
    /** 支付回调已入账，等待 Outbox 推进拼团结算。 */
    PAID,
    /** 团已达到目标人数，订单可以进入履约。 */
    GROUP_SUCCESS,
    /** 商品履约完成，订单生命周期结束。 */
    COMPLETED,
    /** 库存不足、团满等原因导致下单被拒绝。 */
    REJECTED,
    /** 未支付订单超时或被主动取消，预占资源需要释放。 */
    CANCELLED,
    /** 团失败且订单已支付，正在向支付渠道退款。 */
    REFUNDING,
    /** 支付退款和库存返还均已完成。 */
    REFUNDED
}
