package com.linger.module.toolhub.chat.dto;

import lombok.Data;

@Data
public class ChatSendRequest {
    private ChatMessageType type;
    private String content;
}
