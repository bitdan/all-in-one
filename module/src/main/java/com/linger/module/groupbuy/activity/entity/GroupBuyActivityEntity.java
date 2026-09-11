package com.linger.module.groupbuy.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.linger.module.groupbuy.activity.model.ActivityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 拼团活动持久化实体，保存库存、成团规则和活动时间窗口。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_activities")
public class GroupBuyActivityEntity {

    /** 活动主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 活动名称。 */
    private String name;
    /** 商品 SKU 编码。 */
    private String skuId;
    /** 下单单价，使用 BigDecimal 避免金额精度问题。 */
    private BigDecimal unitPrice;
    /** 活动初始库存。 */
    private Integer totalStock;
    /** 单个用户在活动内的限购数量。 */
    private Integer perUserLimit;
    /** 每个团的目标成团人数。 */
    private Integer targetCount;
    /** 订单支付超时秒数。 */
    private Long payTimeoutSeconds;
    /** 开团后允许成团的最长秒数。 */
    private Long groupTimeoutSeconds;
    /** 活动开始时间。 */
    private OffsetDateTime startsAt;
    /** 活动结束时间。 */
    private OffsetDateTime endsAt;
    /** 活动状态。 */
    private ActivityStatus status;
    /** MyBatis-Plus 乐观锁版本号。 */
    @Version
    private Long version;
    /** 创建人用户 ID，0 表示系统。 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    /** 最后修改人用户 ID，0 表示系统。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    /** 最后修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
