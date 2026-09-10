package com.linger.module.toolhub.game.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MakeMoveRequest {

    private String roomId;
    private Integer x;
    private Integer y;
}
