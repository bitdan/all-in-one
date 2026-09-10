package com.linger.module.toolhub.chat.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatEventType {
    CONNECTED("connected"),
    MESSAGE("message"),
    ERROR("error");

    @JsonValue
    private final String value;
}
