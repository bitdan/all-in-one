package com.linger.module.toolhub.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlayerColor {
    BLACK("black", 1),
    WHITE("white", 2);

    @JsonValue
    private final String value;
    private final int boardValue;

    public PlayerColor opposite() {
        return this == BLACK ? WHITE : BLACK;
    }

    @JsonCreator
    public static PlayerColor fromValue(String value) {
        for (PlayerColor color : values()) {
            if (color.value.equalsIgnoreCase(value)) return color;
        }
        throw new IllegalArgumentException("未知玩家颜色: " + value);
    }
}
