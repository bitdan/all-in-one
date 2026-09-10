package com.linger.module.toolhub.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;
    private String password;
    private String confirmPassword;
    private String code;
    private String uuid;
    private String email;
    private String userType;
}
