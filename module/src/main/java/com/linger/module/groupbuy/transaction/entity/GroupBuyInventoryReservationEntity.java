package com.linger.module.groupbuy.transaction.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.linger.module.groupbuy.transaction.model.InventoryReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 订单维度库存预占单，作为确认、释放和退款的幂等屏障。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_inventory_reservations")
public class GroupBuyInventoryReservationEntity {

    @TableId
    private String id;
    private String orderId;
    private Long activityId;
    private String skuId;
    private Integer quantity;
    private InventoryReservationStatus status;
    private OffsetDateTime expireAt;
    @Version
    private Long version;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
