package com.linger.module.toolhub.auth.dto;

import lombok.Value;

@Value
public class UserProfileResponse {
    String userId;
    String username;
    String email;
    String avatar;
}
