package com.linger.module.groupbuy.transaction.model;

/** 库存预占生命周期，状态迁移通过数据库条件更新保证幂等。 */
public enum InventoryReservationStatus {
    /** 下单成功，库存从可用转为预占。 */
    RESERVED,
    /** 支付已确认，库存从预占转为确认。 */
    CONFIRMED,
    /** 未支付订单取消，预占库存已释放。 */
    RELEASED,
    /** 已支付订单退款，库存已返还。 */
    REFUNDED
}
