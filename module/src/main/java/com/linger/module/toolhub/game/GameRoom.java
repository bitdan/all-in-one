package com.linger.module.toolhub.game;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.linger.module.toolhub.game.model.GameStatus;
import com.linger.module.toolhub.game.model.PlayerColor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GameRoom {
    private String roomId;
    private Player host;
    private Player guest;
    private GameState gameState;
    private int spectatorCount;

    @Data
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Player {
        private String userId;
        private String username;
        private PlayerColor color;
        @JsonProperty("is_ready")
        private boolean ready;
        @JsonProperty("is_online")
        private boolean online;
    }

    @Data
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GameState {
        private GameStatus status;
        private int[][] board;
        private PlayerColor currentPlayer;
        private PlayerColor winner;
        private Move lastMove;
        private List<Move> moves = new ArrayList<>();
        private int movesCount;
        private double createdAt;
        private double updatedAt;
    }

    @Data
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Move {
        private int x;
        private int y;
        private PlayerColor color;
        private double timestamp;
    }
}
