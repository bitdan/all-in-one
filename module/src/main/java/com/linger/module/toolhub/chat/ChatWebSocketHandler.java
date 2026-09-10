package com.linger.module.toolhub.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linger.module.toolhub.auth.AuthService;
import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.util.JsonUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@AllArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final AuthService authService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String channel = attribute(session, "channel");
        chatService.connect(channel, session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "connected");
        payload.put("channel", channel);
        chatService.send(session, payload);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> payload = JsonUtils.parseObject(message.getPayload(),
                    new TypeReference<Map<String, Object>>() { });
            String content = payload.get("content") == null ? "" : String.valueOf(payload.get("content")).trim();
            if (content.isEmpty()) {
                sendError(session, "消息不能为空");
                return;
            }
            if (content.length() > 500) content = content.substring(0, 500);
            UserRecord user = authService.getUser(attribute(session, "userId"));
            chatService.publish(attribute(session, "channel"), user, content);
        } catch (Exception exception) {
            sendError(session, "消息格式错误");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        chatService.disconnect(attribute(session, "channel"), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        chatService.disconnect(attribute(session, "channel"), session);
    }

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("message", message);
        chatService.send(session, payload);
    }

    private String attribute(WebSocketSession session, String name) {
        Object value = session.getAttributes().get(name);
        return value == null ? "" : String.valueOf(value);
    }
}
