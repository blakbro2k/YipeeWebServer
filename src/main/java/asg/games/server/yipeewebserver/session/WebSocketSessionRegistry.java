package asg.games.server.yipeewebserver.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionRegistry {

    // primary mapping: your logical sessionId -> WebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession> bySessionId = new ConcurrentHashMap<>();

    /**
     * Bind a logical sessionId to a WebSocketSession.
     * If this WebSocketSession was previously bound under a different sessionId,
     * this will evict the old mapping to prevent leaks.
     */
    public void bind(String sessionId, WebSocketSession ws) {
        if (sessionId == null || sessionId.isBlank() || ws == null) {
            log.debug("bind ignored (sessionId/ws null). sessionId={}, ws={}", sessionId, ws);
            return;
        }

        // Optional safety: ensure the same WS isn't left mapped under a different sessionId
        // This is O(n) but n is typically small (active sockets), and it's worth the correctness.
        evictAnyMappingPointingTo(ws.getId());

        bySessionId.put(sessionId, ws);
        log.debug("WS registry bind: sessionId={} -> wsId={}", sessionId, ws.getId());
    }

    /**
     * Unbind by logical sessionId.
     */
    public void unbind(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        WebSocketSession removed = bySessionId.remove(sessionId);
        if (removed != null) {
            log.debug("WS registry unbind: sessionId={} (wsId={})", sessionId, removed.getId());
        }
    }

    /**
     * Unbind by WebSocketSession object (convenience).
     */
    public void unbind(WebSocketSession ws) {
        if (ws == null) return;
        unbindByWsId(ws.getId());
    }

    /**
     * Unbind all logical sessionIds that currently map to the provided WebSocket id.
     * Useful in afterConnectionClosed, where you might not have the sessionId.
     */
    public void unbindByWsId(String wsId) {
        if (wsId == null || wsId.isBlank()) return;

        int removedCount = 0;
        for (Map.Entry<String, WebSocketSession> e : bySessionId.entrySet()) {
            WebSocketSession ws = e.getValue();
            if (ws != null && wsId.equals(ws.getId())) {
                if (bySessionId.remove(e.getKey(), ws)) {
                    removedCount++;
                }
            }
        }

        if (removedCount > 0) {
            log.debug("WS registry unbindByWsId: wsId={} removedMappings={}", wsId, removedCount);
        }
    }

    public Optional<WebSocketSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        return Optional.ofNullable(bySessionId.get(sessionId));
    }

    public int size() {
        return bySessionId.size();
    }

    private void evictAnyMappingPointingTo(String wsId) {
        // remove any prior entries mapping to this same WS id (avoid leak on rebind)
        for (Map.Entry<String, WebSocketSession> e : bySessionId.entrySet()) {
            WebSocketSession existing = e.getValue();
            if (existing != null && wsId.equals(existing.getId())) {
                bySessionId.remove(e.getKey(), existing);
            }
        }
    }
}