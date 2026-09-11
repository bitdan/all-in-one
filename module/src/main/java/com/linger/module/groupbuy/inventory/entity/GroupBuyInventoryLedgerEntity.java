package com.linger.module.groupbuy.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linger.module.groupbuy.inventory.model.InventoryOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 库存变更流水实体，以 orderId + operation 唯一约束实现幂等记账。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_inventory_ledger")
public class GroupBuyInventoryLedgerEntity {

    /** 库存流水主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 活动 ID。 */
    private Long activityId;
    /** 商品 SKU 编码。 */
    private String skuId;
    /** 触发库存变化的订单 ID。 */
    private String orderId;
    /** 库存操作类型。 */
    private InventoryOperation operation;
    /** 变化数量，方向由 operation 表示。 */
    private Integer quantity;
    /** 创建人用户 ID，0 表示系统。 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    /** 最后修改人用户 ID，库存流水通常不会修改。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    /** 最后修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
