package com.linger.module.toolhub.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent<T> {
    private ChatEventType type;
    private String channel;
    private T message;

    public static ChatEvent<Void> connected(String channel) {
        return new ChatEvent<>(ChatEventType.CONNECTED, channel, null);
    }

    public static ChatEvent<ChatMessage> message(String channel, ChatMessage message) {
        return new ChatEvent<>(ChatEventType.MESSAGE, channel, message);
    }

    public static ChatEvent<String> error(String message) {
        return new ChatEvent<>(ChatEventType.ERROR, null, message);
    }
}
