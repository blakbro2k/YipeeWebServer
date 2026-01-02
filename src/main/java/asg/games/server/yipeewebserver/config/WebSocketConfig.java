package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.net.YipeeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final YipeeWebSocketHandler yipeeWebSocketHandler;
    private final WsLaunchTokenHandshakeInterceptor wsLaunchTokenHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(yipeeWebSocketHandler, "/ws/game")
                .addInterceptors(wsLaunchTokenHandshakeInterceptor)
                .setAllowedOrigins("*"); // tighten later
    }
}
