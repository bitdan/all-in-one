package com.linger.module.toolhub.game;

import com.linger.module.toolhub.auth.AuthService;
import com.linger.module.common.ApiResponse;
import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.game.dto.JoinRoomRequest;
import com.linger.module.toolhub.game.dto.MakeMoveRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/game")
@AllArgsConstructor
public class GameController {

    private final GameService gameService;
    private final AuthService authService;

    @PostMapping("/create-room")
    public ApiResponse<String> createRoom() {
        return ApiResponse.success("房间创建成功", gameService.createRoom(authService.currentUser()));
    }

    @PostMapping("/join-room")
    public ApiResponse<Void> joinRoom(@RequestBody JoinRoomRequest request) {
        String roomId = required(request.getRoomId(), "room_id");
        gameService.join(roomId, authService.currentUser());
        return ApiResponse.success("加入房间成功");
    }

    @PostMapping("/leave-room")
    public ApiResponse<Void> leaveRoom() {
        gameService.leave(authService.currentUserId());
        return ApiResponse.success("离开房间成功");
    }

    @PostMapping("/make-move")
    public ApiResponse<Void> makeMove(@RequestBody MakeMoveRequest request) {
        String roomId = required(request.getRoomId(), "room_id");
        int x = coordinate(request.getX());
        int y = coordinate(request.getY());
        gameService.move(roomId, authService.currentUserId(), x, y);
        return ApiResponse.success("下棋成功");
    }

    @PostMapping("/start-game")
    public ApiResponse<Void> startGame(@RequestParam(name = "room_id") String roomId) {
        gameService.start(roomId, authService.currentUserId());
        return ApiResponse.success("游戏开始");
    }

    @PostMapping("/restart-game")
    public ApiResponse<Void> restartGame(@RequestParam(name = "room_id") String roomId) {
        gameService.restart(roomId, authService.currentUserId());
        return ApiResponse.success("游戏重新开始");
    }

    @GetMapping("/room/{roomId}")
    public ApiResponse<GameRoom> room(@PathVariable String roomId) {
        return ApiResponse.success("获取房间信息成功", gameService.room(roomId, authService.currentUserId()));
    }

    @GetMapping(value = "/events/{roomId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable String roomId,
            @RequestParam(name = "access_token", defaultValue = "") String accessToken,
            @CookieValue(name = "tool_hub_token", defaultValue = "") String cookieToken) {
        String userId = authService.loginIdByToken(accessToken.isEmpty() ? cookieToken : accessToken);
        if (userId == null) throw BusinessException.unauthorized("认证失败，请重新登录");
        return gameService.events(roomId, userId);
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw BusinessException.badRequest(field + " 不能为空");
        return value.trim();
    }

    private int coordinate(Integer value) {
        if (value == null) throw BusinessException.badRequest("坐标格式不正确");
        return value;
    }
}
