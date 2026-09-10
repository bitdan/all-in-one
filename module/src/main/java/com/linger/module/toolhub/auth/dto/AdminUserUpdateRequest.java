package com.linger.module.toolhub.auth.dto;

import com.linger.module.toolhub.auth.model.UserStatus;
import lombok.Data;

import java.util.List;

@Data
public class AdminUserUpdateRequest {

    private String email;
    private String avatar;
    private UserStatus status;
    private List<String> roles;
    private List<String> permissions;
}
