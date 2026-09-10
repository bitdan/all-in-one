package com.linger.module.toolhub.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PostResponse {

    private String id;
    private String title;
    private String category;
    private List<String> tags;
    private String content;
    private String authorId;
    private String authorName;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean canEdit;
}
