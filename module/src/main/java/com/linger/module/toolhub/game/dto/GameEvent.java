package com.linger.module.toolhub.game.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.linger.module.toolhub.game.GameRoom;
import com.linger.module.toolhub.game.model.GameEventType;
import com.linger.module.toolhub.game.model.PlayerColor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameEvent<T> {
    private GameEventType type;
    private String roomId;
    private T data;
    private double timestamp;

    @Value
    public static class PlayerJoinedData {
        GameRoom.Player player;
    }

    @Value
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PlayerLeftData {
        String userId;
    }

    @Value
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GameStartedData {
        PlayerColor currentPlayer;
        PlayerColor hostColor;
        PlayerColor guestColor;
        int[][] board;
    }

    @Value
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MoveData {
        GameRoom.Move move;
        PlayerColor currentPlayer;
        int[][] board;
        PlayerColor winner;
    }
}
