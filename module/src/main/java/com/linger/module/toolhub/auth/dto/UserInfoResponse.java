package com.linger.module.toolhub.auth.dto;

import lombok.Value;

import java.util.List;

@Value
public class UserInfoResponse {
    UserProfileResponse user;
    List<String> roles;
    List<String> permissions;
}
