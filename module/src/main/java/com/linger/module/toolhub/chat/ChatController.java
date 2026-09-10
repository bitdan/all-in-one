package com.linger.module.toolhub.chat;

import com.linger.module.common.ApiResponse;
import com.linger.module.toolhub.auth.AuthService;
import com.linger.module.toolhub.chat.dto.ChatMessage;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@AllArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;

    @GetMapping("/history")
    public ApiResponse<List<ChatMessage>> history(
            @RequestParam(defaultValue = "general") String channel,
            @RequestParam(defaultValue = "50") int limit) {
        authService.currentUserId();
        return ApiResponse.success("获取聊天记录成功", chatService.history(channel, limit));
    }
}
