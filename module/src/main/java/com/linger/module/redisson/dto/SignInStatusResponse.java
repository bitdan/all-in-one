package com.linger.module.redisson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignInStatusResponse {
    boolean success;
    String message;
    Boolean isSigned;
    Integer consecutiveDays;
    String queryDate;
}
