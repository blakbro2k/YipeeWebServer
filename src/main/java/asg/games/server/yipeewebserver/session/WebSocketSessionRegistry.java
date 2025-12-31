package asg.games.server.yipeewebserver.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final ConcurrentHashMap<String, WebSocketSession> bySessionId = new ConcurrentHashMap<>();

    public void bind(String sessionId, WebSocketSession ws) { bySessionId.put(sessionId, ws); }
    public void unbind(String sessionId) { if (sessionId != null) bySessionId.remove(sessionId); }
    public Optional<WebSocketSession> find(String sessionId) { return Optional.ofNullable(bySessionId.get(sessionId)); }
}
