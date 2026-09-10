package com.linger.module.toolhub.game;

import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.config.ToolHubProperties;
import com.linger.module.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameService {

    private final RedissonClient redissonClient;
    private final ToolHubProperties properties;
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Set<String> subscribedRooms = ConcurrentHashMap.newKeySet();

    public String createRoom(UserRecord user) {
        String existing = playerRooms().get(user.getUserId());
        if (existing != null && load(existing) != null) throw BusinessException.badRequest("您已在其他房间中，请先离开");
        if (existing != null) playerRooms().remove(user.getUserId());
        String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        double now = now();
        GameRoom room = new GameRoom();
        room.setRoomId(roomId);
        room.setHost(player(user, "black"));
        GameRoom.GameState state = new GameRoom.GameState();
        state.setStatus("waiting");
        state.setBoard(new int[15][15]);
        state.setCurrentPlayer("black");
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        room.setGameState(state);
        save(room);
        playerRooms().put(user.getUserId(), roomId);
        return roomId;
    }

    public void join(String roomId, UserRecord user) {
        withRoomLock(roomId, () -> {
            String existing = playerRooms().get(user.getUserId());
            if (existing != null && load(existing) != null) throw BusinessException.badRequest("您已在其他房间中，请先离开");
            GameRoom room = requiredRoom(roomId);
            if (room.getGuest() != null) throw BusinessException.badRequest("加入房间失败，房间不存在或已满");
            room.setGuest(player(user, "white"));
            room.getGameState().setStatus("playing");
            room.getGameState().setUpdatedAt(now());
            save(room);
            playerRooms().put(user.getUserId(), roomId);
            Map<String, Object> joined = new LinkedHashMap<>();
            joined.put("player", room.getGuest());
            publish(roomId, "player_joined", joined);
            Map<String, Object> started = new LinkedHashMap<>();
            started.put("current_player", "black");
            started.put("host_color", "black");
            started.put("guest_color", "white");
            started.put("board", room.getGameState().getBoard());
            publish(roomId, "game_started", started);
        });
    }

    public void leave(String userId) {
        String roomId = playerRooms().get(userId);
        if (roomId == null) throw BusinessException.badRequest("您不在任何房间中");
        withRoomLock(roomId, () -> {
            GameRoom room = load(roomId);
            if (room == null) {
                playerRooms().remove(userId);
                return;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("user_id", userId);
            publish(roomId, "player_left", event);
            if (room.getHost().getUserId().equals(userId)) {
                deleteRoom(room);
            } else if (room.getGuest() != null && room.getGuest().getUserId().equals(userId)) {
                room.setGuest(null);
                room.getGameState().setStatus("waiting");
                room.getGameState().setUpdatedAt(now());
                playerRooms().remove(userId);
                save(room);
            }
        });
    }

    public void move(String roomId, String userId, int x, int y) {
        withRoomLock(roomId, () -> {
            GameRoom room = requiredMembership(roomId, userId);
            GameRoom.GameState state = room.getGameState();
            if (!"playing".equals(state.getStatus()) || x < 0 || x >= 15 || y < 0 || y >= 15 || state.getBoard()[y][x] != 0) {
                throw BusinessException.badRequest("下棋失败，请检查位置和游戏状态");
            }
            String color = playerColor(room, userId);
            if (color == null || !color.equals(state.getCurrentPlayer())) throw BusinessException.badRequest("还未轮到您落子");
            int colorValue = "black".equals(color) ? 1 : 2;
            state.getBoard()[y][x] = colorValue;
            GameRoom.Move move = new GameRoom.Move();
            move.setX(x);
            move.setY(y);
            move.setColor(color);
            move.setTimestamp(now());
            state.getMoves().add(move);
            state.setMovesCount(state.getMoves().size());
            state.setLastMove(move);
            state.setUpdatedAt(now());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("move", move);
            if (winner(state.getBoard(), x, y, colorValue)) {
                state.setWinner(color);
                state.setStatus("finished");
                data.put("winner", color);
                save(room);
                publish(roomId, "game_ended", data);
            } else {
                state.setCurrentPlayer("black".equals(color) ? "white" : "black");
                data.put("current_player", state.getCurrentPlayer());
                data.put("board", state.getBoard());
                save(room);
                publish(roomId, "move_made", data);
            }
        });
    }

    public void start(String roomId, String userId) {
        withRoomLock(roomId, () -> {
            GameRoom room = requiredMembership(roomId, userId);
            if (!room.getHost().getUserId().equals(userId) || room.getGuest() == null) {
                throw BusinessException.badRequest("开始游戏失败，请检查权限和房间状态");
            }
            room.getGameState().setStatus("playing");
            room.getGameState().setUpdatedAt(now());
            save(room);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("current_player", room.getGameState().getCurrentPlayer());
            data.put("board", room.getGameState().getBoard());
            publish(roomId, "game_started", data);
        });
    }

    public void restart(String roomId, String userId) {
        withRoomLock(roomId, () -> {
            GameRoom room = requiredMembership(roomId, userId);
            if (!room.getHost().getUserId().equals(userId)) throw BusinessException.badRequest("重新开始游戏失败，请检查权限");
            GameRoom.GameState state = room.getGameState();
            state.setBoard(new int[15][15]);
            state.setCurrentPlayer("black");
            state.setWinner(null);
            state.setLastMove(null);
            state.setMoves(new ArrayList<>());
            state.setMovesCount(0);
            state.setStatus(room.getGuest() == null ? "waiting" : "playing");
            state.setUpdatedAt(now());
            save(room);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("current_player", "black");
            data.put("board", state.getBoard());
            publish(roomId, "game_started", data);
        });
    }

    public GameRoom room(String roomId, String userId) {
        return requiredMembership(roomId, userId);
    }

    public SseEmitter events(String roomId, String userId) {
        GameRoom room = requiredMembership(roomId, userId);
        subscribe(roomId);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(roomId, emitter));
        emitter.onTimeout(() -> removeEmitter(roomId, emitter));
        emitter.onError(error -> removeEmitter(roomId, emitter));
        send(emitter, envelope("connected", roomId, new LinkedHashMap<>()));
        send(emitter, envelope("room_state", roomId, room));
        return emitter;
    }

    @Scheduled(fixedDelay = 20000)
    public void heartbeat() {
        for (Map.Entry<String, Set<SseEmitter>> entry : emitters.entrySet()) {
            Map<String, Object> payload = envelope("heartbeat", entry.getKey(), new LinkedHashMap<>());
            for (SseEmitter emitter : new ArrayList<>(entry.getValue())) send(emitter, payload);
        }
    }

    private void subscribe(String roomId) {
        if (!subscribedRooms.add(roomId)) return;
        RTopic topic = redissonClient.getTopic(topicKey(roomId), StringCodec.INSTANCE);
        topic.addListener(String.class, (channel, payload) -> {
            Set<SseEmitter> roomEmitters = emitters.get(roomId);
            if (roomEmitters == null) return;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> event = JsonUtils.parseObject(payload, Map.class);
                for (SseEmitter emitter : new ArrayList<>(roomEmitters)) send(emitter, event);
            } catch (Exception ignored) {
            }
        });
    }

    private void publish(String roomId, String type, Object data) {
        try {
            subscribe(roomId);
            redissonClient.getTopic(topicKey(roomId), StringCodec.INSTANCE)
                    .publish(JsonUtils.toJsonString(envelope(type, roomId, data)));
        } catch (Exception exception) {
            throw new IllegalStateException("棋局事件发布失败", exception);
        }
    }

    private Map<String, Object> envelope(String type, String roomId, Object data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("room_id", roomId);
        event.put("data", data);
        event.put("timestamp", now());
        return event;
    }

    private void send(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (Exception exception) {
            emitters.values().forEach(set -> set.remove(emitter));
            emitter.complete();
        }
    }

    private void removeEmitter(String roomId, SseEmitter emitter) {
        Set<SseEmitter> roomEmitters = emitters.get(roomId);
        if (roomEmitters != null) roomEmitters.remove(emitter);
    }

    private GameRoom.Player player(UserRecord user, String color) {
        GameRoom.Player player = new GameRoom.Player();
        player.setUserId(user.getUserId());
        player.setUsername(user.getUsername());
        player.setColor(color);
        player.setReady(true);
        player.setOnline(true);
        return player;
    }

    private GameRoom requiredRoom(String roomId) {
        GameRoom room = load(roomId);
        if (room == null) throw BusinessException.notFound("房间不存在");
        return room;
    }

    private GameRoom requiredMembership(String roomId, String userId) {
        GameRoom room = requiredRoom(roomId);
        boolean member = room.getHost().getUserId().equals(userId) ||
                (room.getGuest() != null && room.getGuest().getUserId().equals(userId));
        if (!member) throw BusinessException.forbidden("您不在该房间中");
        return room;
    }

    private String playerColor(GameRoom room, String userId) {
        if (room.getHost().getUserId().equals(userId)) return room.getHost().getColor();
        if (room.getGuest() != null && room.getGuest().getUserId().equals(userId)) return room.getGuest().getColor();
        return null;
    }

    private boolean winner(int[][] board, int x, int y, int color) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] direction : directions) {
            int count = 1 + count(board, x, y, direction[0], direction[1], color)
                    + count(board, x, y, -direction[0], -direction[1], color);
            if (count >= 5) return true;
        }
        return false;
    }

    private int count(int[][] board, int x, int y, int dx, int dy, int color) {
        int count = 0;
        for (int step = 1; step < 5; step++) {
            int nextX = x + dx * step;
            int nextY = y + dy * step;
            if (nextX < 0 || nextX >= 15 || nextY < 0 || nextY >= 15 || board[nextY][nextX] != color) break;
            count++;
        }
        return count;
    }

    private GameRoom load(String roomId) {
        String value = roomBucket(roomId).get();
        if (value == null) return null;
        try { return JsonUtils.parseObject(value, GameRoom.class); }
        catch (Exception exception) { throw new IllegalStateException("房间数据读取失败", exception); }
    }

    private void save(GameRoom room) {
        try {
            roomBucket(room.getRoomId()).set(JsonUtils.toJsonString(room), properties.getGameRoomTtlSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("房间数据保存失败", exception);
        }
    }

    private void deleteRoom(GameRoom room) {
        roomBucket(room.getRoomId()).delete();
        playerRooms().remove(room.getHost().getUserId());
        if (room.getGuest() != null) playerRooms().remove(room.getGuest().getUserId());
        Set<SseEmitter> roomEmitters = emitters.remove(room.getRoomId());
        if (roomEmitters != null) roomEmitters.forEach(SseEmitter::complete);
    }

    private void withRoomLock(String roomId, Runnable action) {
        RLock lock = redissonClient.getLock("toolhub:game:lock:" + roomId);
        lock.lock(10, TimeUnit.SECONDS);
        try { action.run(); }
        finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
    }

    private RBucket<String> roomBucket(String roomId) { return redissonClient.getBucket("toolhub:game:room:" + roomId, StringCodec.INSTANCE); }
    private RMap<String, String> playerRooms() { return redissonClient.getMap("toolhub:game:player-rooms", StringCodec.INSTANCE); }
    private String topicKey(String roomId) { return "toolhub:game:events:" + roomId; }
    private double now() { return System.currentTimeMillis() / 1000.0D; }
}
