package com.linger.module.groupbuy.group.model;

public enum GroupMemberStatus {
    /** 已占用团名额，尚未支付。 */
    RESERVED,
    /** 成员订单已支付，等待整团成功。 */
    PAID,
    /** 团成功后成员资格已确认。 */
    CONFIRMED,
    /** 未支付席位已取消并释放。 */
    CANCELLED,
    /** 团失败后成员订单正在退款。 */
    REFUNDING,
    /** 退款和资源返还完成。 */
    REFUNDED
}
