package com.linger.module.toolhub.auth.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.linger.module.toolhub.auth.UserRecord;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdminUserPageResponse {
    List<AdminUserResponse> items;
    Long total;
    Long totalActive;
    Long totalDisabled;
    Long totalFrozen;
    Long page;
    Long pageSize;

    public static AdminUserPageResponse from(IPage<UserRecord> page, UserStatusSummary summary) {
        List<AdminUserResponse> items = new ArrayList<>(page.getRecords().size());
        for (UserRecord user : page.getRecords()) {
            items.add(AdminUserResponse.from(user));
        }
        return new AdminUserPageResponse(
                items,
                page.getTotal(),
                summary.getTotalActive(),
                summary.getTotalDisabled(),
                summary.getTotalFrozen(),
                page.getCurrent(),
                page.getSize());
    }
}
