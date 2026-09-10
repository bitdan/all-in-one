package com.linger.module.toolhub.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminUserUpdateRequest {

    private String email;
    private String avatar;
    private String status;
    private List<String> roles;
    private List<String> permissions;
}
