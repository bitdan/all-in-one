package com.linger.module.toolhub.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameEventType {
    CONNECTED("connected"),
    ROOM_STATE("room_state"),
    PLAYER_JOINED("player_joined"),
    PLAYER_LEFT("player_left"),
    GAME_STARTED("game_started"),
    MOVE_MADE("move_made"),
    GAME_ENDED("game_ended"),
    HEARTBEAT("heartbeat"),
    ERROR("error");

    @JsonValue
    private final String value;

    @JsonCreator
    public static GameEventType fromValue(String value) {
        for (GameEventType type : values()) {
            if (type.value.equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("未知游戏事件类型: " + value);
    }
}
