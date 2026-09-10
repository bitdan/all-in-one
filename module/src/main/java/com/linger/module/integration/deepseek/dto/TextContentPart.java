package com.linger.module.integration.deepseek.dto;

import lombok.Getter;

@Getter
public final class TextContentPart implements DeepSeekContentPart {

    private final String type = "text";
    private final String text;

    public TextContentPart(String text) {
        this.text = text;
    }
}
