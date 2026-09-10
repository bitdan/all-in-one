package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeepSeekImageDetail {
    LOW("low"),
    HIGH("high"),
    ORIGINAL("original"),
    AUTO("auto");

    private final String value;

    DeepSeekImageDetail(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
