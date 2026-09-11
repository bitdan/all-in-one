package com.linger.module.groupbuy.inventory.model;

public enum InventoryOperation {
    /** 下单时把可用库存转为预占库存。 */
    RESERVE,
    /** 支付确认后把预占库存转为确认库存。 */
    CONFIRM,
    /** 未支付订单取消后把预占库存退回可用库存。 */
    RELEASE,
    /** 已支付订单退款后把确认库存退回可用库存。 */
    REFUND
}
