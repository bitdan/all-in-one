package com.linger.module.toolhub.post.dto;

import com.linger.module.common.page.PageQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class PostListQuery extends PageQuery {

    private String keyword;
    private String category;
    private String tag;
}
