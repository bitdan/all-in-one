package com.linger.module.groupbuy.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.linger.module.groupbuy.order.model.GroupOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 拼团订单实体，同时保存商品价格快照、支付信息和状态机版本。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_orders")
public class GroupBuyOrderEntity {

    /** 下单前生成的订单 UUID，可同时作为 Redis 预占标识。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 客户端请求幂等号。 */
    private String requestId;
    /** 下单用户 ID。 */
    private Long userId;
    /** 活动 ID。 */
    private Long activityId;
    /** 团 ID。 */
    private Long groupId;
    /** 商品 SKU 快照。 */
    private String skuId;
    /** 购买数量。 */
    private Integer quantity;
    /** 下单时活动单价快照。 */
    private BigDecimal unitPrice;
    /** 优惠总金额。 */
    private BigDecimal discountAmount;
    /** 最终应付金额。 */
    private BigDecimal payableAmount;
    /** 订单生命周期状态。 */
    private GroupOrderStatus status;
    /** 预占失败或补偿关闭原因。 */
    private String rejectReason;
    /** Redis 库存和团名额预占标识。 */
    private String reservationId;
    /** 支付渠道流水号，全局唯一。 */
    private String paymentNo;
    /** 支付截止时间。 */
    private OffsetDateTime payDeadline;
    /** 支付完成时间。 */
    private OffsetDateTime paidAt;
    /** MyBatis-Plus 乐观锁版本号。 */
    @Version
    private Long version;
    /** 创建人用户 ID，通常等于 userId。 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    /** 最后修改人用户 ID，0 表示系统任务或支付回调。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    /** 最后修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
