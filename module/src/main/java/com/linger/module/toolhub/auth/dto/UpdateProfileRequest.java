package com.linger.module.toolhub.auth.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String email;
    private String avatar;
}
