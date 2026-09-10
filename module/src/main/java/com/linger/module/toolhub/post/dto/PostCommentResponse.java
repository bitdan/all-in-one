package com.linger.module.toolhub.post.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PostCommentResponse {

    private String id;
    private String postId;
    private String parentId;
    private String authorId;
    private String authorName;
    private String replyToAuthorName;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean canEdit;
}
