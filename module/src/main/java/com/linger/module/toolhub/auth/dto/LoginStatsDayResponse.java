package com.linger.module.toolhub.auth.dto;

import lombok.Value;

import java.time.LocalDate;

@Value
public class LoginStatsDayResponse {
    LocalDate date;
    boolean logged;
}
