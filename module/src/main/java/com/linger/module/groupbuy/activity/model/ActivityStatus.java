package com.linger.module.groupbuy.activity.model;

public enum ActivityStatus {
    /** 活动配置完成，尚未向 Redis 发布库存。 */
    READY,
    /** 活动已发布，可在有效时间窗口内开团和下单。 */
    RUNNING,
    /** 活动自然到达结束时间。 */
    ENDED,
    /** 活动被运营人员提前关闭。 */
    CLOSED
}
