package com.linger.module.groupbuy.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linger.module.groupbuy.group.model.GroupMemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 拼团成员实体，确保同一用户在同一团内只能拥有一个有效席位。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_members")
public class GroupBuyMemberEntity {

    /** 成员记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 团 ID。 */
    private Long groupId;
    /** 成员用户 ID。 */
    private Long userId;
    /** 成员对应订单 ID。 */
    private String orderId;
    /** 成员状态。 */
    private GroupMemberStatus status;
    /** 创建人用户 ID。 */
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
