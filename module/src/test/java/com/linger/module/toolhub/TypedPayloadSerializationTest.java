package com.linger.module.toolhub;

import com.linger.module.toolhub.auth.dto.AdminUserResponse;
import com.linger.module.toolhub.auth.dto.AdminUserUpdateRequest;
import com.linger.module.toolhub.auth.model.UserStatus;
import com.linger.module.toolhub.chat.dto.ChatEvent;
import com.linger.module.toolhub.chat.dto.ChatMessage;
import com.linger.module.toolhub.chat.dto.ChatMessageType;
import com.linger.module.toolhub.game.GameRoom;
import com.linger.module.toolhub.game.dto.GameEvent;
import com.linger.module.toolhub.game.model.GameEventType;
import com.linger.module.toolhub.game.model.GameStatus;
import com.linger.module.toolhub.game.model.PlayerColor;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountRequest;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountResponse;
import com.linger.module.toolhub.twofactor.model.TotpAlgorithm;
import com.linger.module.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class TypedPayloadSerializationTest {

    @Test
    void shouldKeepAuthJsonContractWithTypedResponsesAndStatusEnum() {
        AdminUserResponse response = new AdminUserResponse(
                "user-1", "alice", "alice@example.com", null, UserStatus.ACTIVE,
                Collections.singletonList("user"), Collections.emptyList(),
                OffsetDateTime.of(2026, 9, 10, 8, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 9, 10, 9, 0, 0, 0, ZoneOffset.UTC));

        String json = JsonUtils.toJsonString(response);
        AdminUserUpdateRequest request = JsonUtils.parseObject("{\"status\":\"frozen\"}",
                AdminUserUpdateRequest.class);

        assertTrue(json.contains("\"user_id\":\"user-1\""));
        assertTrue(json.contains("\"status\":\"active\""));
        assertTrue(json.contains("\"created_at\":"));
        assertEquals(UserStatus.FROZEN, request.getStatus());
        log.info("类型化用户响应保持原 JSON 契约：{}", json);
    }

    @Test
    void shouldSerializeAndReadTypedChatPayloads() {
        ChatMessage message = new ChatMessage(
                "message-1", "general", ChatMessageType.MESSAGE,
                "user-1", "alice", "hello", 1_700_000_000D);

        String messageJson = JsonUtils.toJsonString(message);
        ChatMessage restored = JsonUtils.parseObject(messageJson, ChatMessage.class);
        String eventJson = JsonUtils.toJsonString(ChatEvent.message("general", message));

        assertTrue(messageJson.contains("\"user_id\":\"user-1\""));
        assertTrue(messageJson.contains("\"created_at\":1.7E9"));
        assertEquals(ChatMessageType.MESSAGE, restored.getType());
        assertTrue(eventJson.contains("\"type\":\"message\""));
        assertTrue(eventJson.contains("\"message\":{"));
        log.info("类型化聊天消息与事件序列化成功：{}", eventJson);
    }

    @Test
    void shouldReadLegacyGameJsonAndSerializeTypedEvents() {
        String legacyJson = "{\"room_id\":\"room-1\",\"host\":{"
                + "\"user_id\":\"user-1\",\"username\":\"alice\",\"color\":\"black\","
                + "\"is_ready\":true,\"is_online\":true},\"game_state\":{"
                + "\"status\":\"playing\",\"board\":[[0]],\"current_player\":\"white\"}}";
        GameRoom room = JsonUtils.parseObject(legacyJson, GameRoom.class);
        GameEvent<GameEvent.GameStartedData> event = new GameEvent<>(
                GameEventType.GAME_STARTED,
                room.getRoomId(),
                new GameEvent.GameStartedData(PlayerColor.WHITE, PlayerColor.BLACK,
                        PlayerColor.WHITE, room.getGameState().getBoard()),
                1_700_000_000D);

        String eventJson = JsonUtils.toJsonString(event);

        assertEquals(PlayerColor.BLACK, room.getHost().getColor());
        assertEquals(GameStatus.PLAYING, room.getGameState().getStatus());
        assertEquals(PlayerColor.WHITE, room.getGameState().getCurrentPlayer());
        assertTrue(eventJson.contains("\"type\":\"game_started\""));
        assertTrue(eventJson.contains("\"current_player\":\"white\""));
        assertTrue(eventJson.contains("\"host_color\":\"black\""));
        log.info("旧棋局 JSON 可读取，类型化事件保持原契约：{}", eventJson);
    }

    @Test
    void shouldUseTypedTwoFactorModelsAndKeepCamelCaseContract() {
        TwoFactorAccountRequest request = JsonUtils.parseObject(
                "{\"account_name\":\"alice@example.com\",\"algorithm\":\"sha-256\"}",
                TwoFactorAccountRequest.class);
        assertEquals("alice@example.com", request.getAccountName());
        assertEquals(TotpAlgorithm.SHA256, request.getAlgorithm());

        TwoFactorAccountResponse response = TwoFactorAccountResponse.builder()
                .id("totp_1")
                .accountName("alice@example.com")
                .algorithm(TotpAlgorithm.SHA256)
                .secondsRemaining(18L)
                .build();
        String json = JsonUtils.toJsonString(response);
        assertTrue(json.contains("\"accountName\":\"alice@example.com\""));
        assertTrue(json.contains("\"algorithm\":\"SHA256\""));
        assertTrue(json.contains("\"secondsRemaining\":18"));
        assertFalse(json.contains("\"secret\""));
    }
}
