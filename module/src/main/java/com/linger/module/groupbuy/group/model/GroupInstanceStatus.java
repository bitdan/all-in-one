package com.linger.module.groupbuy.group.model;

public enum GroupInstanceStatus {
    /** 团记录已创建，Redis 团状态尚未初始化。 */
    INIT,
    /** 团可接受成员预占和支付。 */
    OPEN,
    /** 已支付人数达到目标人数，成团成功。 */
    SUCCESS,
    /** 截止时间到达但人数不足，相关订单需要取消或退款。 */
    FAILED,
    /** 团被运营人员提前关闭。 */
    CLOSED
}
