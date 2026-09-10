package com.linger.module.toolhub.chat.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatMessageType {
    MESSAGE("message"),
    SYSTEM("system");

    @JsonValue
    private final String value;

    @JsonCreator
    public static ChatMessageType fromValue(String value) {
        for (ChatMessageType type : values()) {
            if (type.value.equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("未知聊天消息类型: " + value);
    }
}
