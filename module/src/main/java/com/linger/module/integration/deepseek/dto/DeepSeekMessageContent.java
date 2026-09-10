package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DeepSeekMessageContent {

    private final Object value;

    private DeepSeekMessageContent(Object value) {
        this.value = value;
    }

    public static DeepSeekMessageContent text(String text) {
        return new DeepSeekMessageContent(text);
    }

    public static DeepSeekMessageContent parts(List<? extends DeepSeekContentPart> parts) {
        List<DeepSeekContentPart> copy = parts == null
                ? Collections.emptyList()
                : new ArrayList<>(parts);
        return new DeepSeekMessageContent(Collections.unmodifiableList(copy));
    }

    @JsonValue
    public Object getValue() {
        return value;
    }
}
