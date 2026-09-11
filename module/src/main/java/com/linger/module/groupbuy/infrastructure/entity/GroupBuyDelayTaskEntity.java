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

/** 可靠延迟任务实体；数据库负责持久化，时间轮只负责近期任务调度。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("groupbuy_delay_tasks")
public class GroupBuyDelayTaskEntity {

    /** 延迟任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 任务类型。 */
    private String taskType;
    /** 关联业务主键。 */
    private String businessId;
    /** 计划执行时间。 */
    private OffsetDateTime executeAt;
    /** PENDING、CLAIMED、RUNNING、DONE 或 DEAD。 */
    private String status;
    /** 已失败重试次数。 */
    private Integer retryCount;
    /** 当前抢占任务的工作节点。 */
    private String workerId;
    /** 工作节点租约截止时间。 */
    private OffsetDateTime lockedUntil;
    /** 最近一次执行错误摘要。 */
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
