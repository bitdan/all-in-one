package com.linger.module.toolhub.auth.dto;

import lombok.Data;

@Data
public class UserStatusSummary {
    private Long totalActive;
    private Long totalDisabled;
    private Long totalFrozen;
}
