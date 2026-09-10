package com.linger.module.toolhub.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linger.module.common.persistence.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("post_post_tags")
public class PostTagEntity extends BaseAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String postId;
    private String tagName;
}
