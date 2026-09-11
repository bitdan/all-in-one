package com.linger.module.redisson.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignInRangeResponse {
    boolean success;
    String message;
    String rangeStats;
    String startDate;
    String endDate;
}
