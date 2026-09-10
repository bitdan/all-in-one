package com.linger.module.toolhub.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LoginStatsResponse {
    LocalDate today;
    boolean loggedToday;
    long currentYearActiveDays;
    int currentMonthActiveDays;
    int recent30DaysActiveDays;
    int consecutiveDays;
    List<LoginStatsDayResponse> recentDays;
}
