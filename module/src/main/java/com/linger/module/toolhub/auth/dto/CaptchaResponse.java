package com.linger.module.toolhub.auth.dto;

import lombok.Value;

@Value
public class CaptchaResponse {
    boolean captchaEnabled;
    String uuid;
    String img;
}
