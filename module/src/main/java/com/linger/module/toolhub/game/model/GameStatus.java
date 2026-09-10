package com.linger.module.toolhub.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameStatus {
    WAITING("waiting"),
    READY("ready"),
    PLAYING("playing"),
    FINISHED("finished");

    @JsonValue
    private final String value;

    @JsonCreator
    public static GameStatus fromValue(String value) {
        for (GameStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) return status;
        }
        throw new IllegalArgumentException("未知游戏状态: " + value);
    }
}
