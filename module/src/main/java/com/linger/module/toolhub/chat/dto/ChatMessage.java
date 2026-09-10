package com.linger.module.toolhub.chat.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatMessage {
    private String id;
    private String channel;
    private ChatMessageType type;
    private String userId;
    private String username;
    private String content;
    private double createdAt;
}
