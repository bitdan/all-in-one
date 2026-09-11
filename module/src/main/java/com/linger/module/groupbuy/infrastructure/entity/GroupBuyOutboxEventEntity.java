package com.linger.module.groupbuy.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 本地事务消息实体，支持事件抢占、租约、重试和死信。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_outbox_events")
public class GroupBuyOutboxEventEntity {

    /** 事件 UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;
    /** 事件类型。 */
    private String eventType;
    /** 聚合类型，例如 ORDER 或 GROUP。 */
    private String aggregateType;
    /** 聚合业务主键。 */
    private String aggregateId;
    /** JSON 格式的事件扩展数据。 */
    private String payload;
    /** PENDING、PROCESSING、DONE 或 DEAD。 */
    private String status;
    /** 下一次允许处理的时间。 */
    private OffsetDateTime availableAt;
    /** 已失败重试次数。 */
    private Integer retryCount;
    /** 当前抢占事件的工作节点。 */
    private String lockedBy;
    /** 工作节点租约截止时间。 */
    private OffsetDateTime lockedUntil;
    /** 最近一次处理错误摘要。 */
    private String lastError;
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
