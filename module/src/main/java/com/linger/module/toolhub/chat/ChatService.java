package com.linger.module.toolhub.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.toolhub.config.ToolHubProperties;
import com.linger.module.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RList;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RedissonClient redissonClient;
    private final ToolHubProperties properties;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();

    public List<Map<String, Object>> history(String channel, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, properties.getChatHistoryLimit()));
        RList<String> list = redissonClient.getList(historyKey(channel), StringCodec.INSTANCE);
        int size = list.size();
        if (size == 0) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String value : list.range(Math.max(0, size - safeLimit), size - 1)) {
            try {
                result.add(JsonUtils.parseObject(value, new TypeReference<Map<String, Object>>() { }));
            } catch (Exception ignored) {
                // Skip malformed legacy entries without breaking the entire chat history.
            }
        }
        return result;
    }

    public void connect(String channel, WebSocketSession session) {
        sessions.computeIfAbsent(channel, key -> ConcurrentHashMap.newKeySet()).add(session);
        subscribe(channel);
    }

    public void disconnect(String channel, WebSocketSession session) {
        Set<WebSocketSession> channelSessions = sessions.get(channel);
        if (channelSessions == null) return;
        channelSessions.remove(session);
        if (channelSessions.isEmpty()) sessions.remove(channel);
    }

    public void publish(String channel, UserRecord user, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", UUID.randomUUID().toString());
        message.put("channel", channel);
        message.put("type", "message");
        message.put("user_id", user.getUserId());
        message.put("username", user.getUsername());
        message.put("content", content);
        message.put("created_at", System.currentTimeMillis() / 1000.0D);
        try {
            String serializedMessage = JsonUtils.toJsonString(message);
            RList<String> history = redissonClient.getList(historyKey(channel), StringCodec.INSTANCE);
            history.add(serializedMessage);
            int max = properties.getChatHistoryLimit();
            while (max > 0 && history.size() > max) history.remove(0);
            if (properties.getChatHistoryTtlSeconds() > 0) {
                history.expire(properties.getChatHistoryTtlSeconds(), TimeUnit.SECONDS);
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "message");
            event.put("channel", channel);
            event.put("message", message);
            redissonClient.getTopic(topicKey(channel), StringCodec.INSTANCE)
                    .publish(JsonUtils.toJsonString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("聊天消息发布失败", exception);
        }
    }

    public void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(JsonUtils.toJsonString(payload)));
        } catch (Exception ignored) {
            // Connection cleanup is handled by the WebSocket handler.
        }
    }

    private void subscribe(String channel) {
        if (!subscribedChannels.add(channel)) return;
        RTopic topic = redissonClient.getTopic(topicKey(channel), StringCodec.INSTANCE);
        topic.addListener(String.class, (name, payload) -> broadcast(channel, payload));
    }

    private void broadcast(String channel, String payload) {
        Set<WebSocketSession> channelSessions = sessions.get(channel);
        if (channelSessions == null) return;
        for (WebSocketSession session : new ArrayList<>(channelSessions)) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(payload));
                else disconnect(channel, session);
            } catch (Exception exception) {
                disconnect(channel, session);
            }
        }
    }

    private String historyKey(String channel) { return "chat:history:" + channel; }
    private String topicKey(String channel) { return "chat:pubsub:" + channel; }
}
