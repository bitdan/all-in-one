package com.linger.module.toolhub.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linger.module.common.persistence.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("post_comments")
public class PostCommentEntity extends BaseAuditEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String postId;
    private String parentId;
    private String authorId;
    private String authorName;
    private String replyToAuthorName;
    private String content;
    private String status;
}
