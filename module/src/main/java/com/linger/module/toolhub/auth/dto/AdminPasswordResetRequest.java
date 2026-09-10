package com.linger.module.toolhub.auth.dto;

import lombok.Data;

@Data
public class AdminPasswordResetRequest {

    private String newPassword;
    private String confirmPassword;
}
