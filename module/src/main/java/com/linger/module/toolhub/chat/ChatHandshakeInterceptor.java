package com.linger.module.toolhub.chat;

import com.linger.module.toolhub.auth.AuthService;
import lombok.AllArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Component
@AllArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthService authService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest)) return false;
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String token = servletRequest.getParameter("access_token");
        if ((token == null || token.isEmpty()) && servletRequest.getCookies() != null) {
            for (Cookie cookie : servletRequest.getCookies()) {
                if ("tool_hub_token".equals(cookie.getName())) token = cookie.getValue();
            }
        }
        String userId = authService.loginIdByToken(token);
        if (userId == null) return false;
        String channel = servletRequest.getParameter("channel");
        attributes.put("userId", userId);
        attributes.put("channel", channel == null || channel.trim().isEmpty() ? "general" : channel.trim());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
