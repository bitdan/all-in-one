package com.linger.module.toolhub.auth.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("active"),
    DISABLED("disabled"),
    FROZEN("frozen"),
    DELETED("deleted");

    @EnumValue
    @JsonValue
    private final String value;

    @JsonCreator
    public static UserStatus fromValue(String value) {
        for (UserStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知用户状态: " + value);
    }
}
