package com.linger.module.integration.deepseek.dto;

import lombok.Getter;

import java.util.List;

@Getter
public final class DeepSeekMessage {

    private final String role;
    private final DeepSeekMessageContent content;

    private DeepSeekMessage(String role, DeepSeekMessageContent content) {
        this.role = role;
        this.content = content;
    }

    public static DeepSeekMessage system(String content) {
        return new DeepSeekMessage("system", DeepSeekMessageContent.text(content));
    }

    public static DeepSeekMessage user(String content) {
        return new DeepSeekMessage("user", DeepSeekMessageContent.text(content));
    }

    public static DeepSeekMessage user(List<? extends DeepSeekContentPart> content) {
        return new DeepSeekMessage("user", DeepSeekMessageContent.parts(content));
    }
}
