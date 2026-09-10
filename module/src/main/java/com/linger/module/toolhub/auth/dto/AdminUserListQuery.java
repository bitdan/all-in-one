package com.linger.module.toolhub.auth.dto;

import com.linger.module.common.page.PageQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AdminUserListQuery extends PageQuery {

    private String keyword;
}
