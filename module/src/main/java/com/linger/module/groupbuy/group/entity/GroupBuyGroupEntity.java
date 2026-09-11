package com.linger.module.groupbuy.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.linger.module.groupbuy.group.model.GroupInstanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 拼团实例实体，一个活动可以创建多个独立的团。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_groups")
public class GroupBuyGroupEntity {

    /** 团主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属活动 ID。 */
    private Long activityId;
    /** 开团用户 ID。 */
    private Long creatorUserId;
    /** 目标成团人数快照。 */
    private Integer targetCount;
    /** 已占用名额，包含未支付和已支付成员。 */
    private Integer reservedCount;
    /** 已支付人数。 */
    private Integer paidCount;
    /** 团生命周期状态。 */
    private GroupInstanceStatus status;
    /** 成团截止时间。 */
    private OffsetDateTime expireAt;
    /** MyBatis-Plus 乐观锁版本号。 */
    @Version
    private Long version;
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
