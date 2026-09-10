package com.linger.module.toolhub.chat;

import com.linger.module.toolhub.config.ToolHubProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@AllArgsConstructor
public class ChatWebSocketConfiguration implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor interceptor;
    private final ToolHubProperties properties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/chat/ws")
                .addInterceptors(interceptor)
                .setAllowedOrigins(properties.corsOriginList().toArray(new String[0]));
    }
}
