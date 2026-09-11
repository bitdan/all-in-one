package com.linger.module.redisson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignInConsecutiveResponse {
    boolean success;
    String message;
    Integer consecutiveDays;
    String queryDate;
}
