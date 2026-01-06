package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.session.GameSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GameSessionStore {

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    public void upsert(GameSession session) {
        sessions.put(session.sessionId(), session);
    }

    public Optional<GameSession> findBySessionId(String sessionId) {
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void clear(String sessionId) {

    }

    public void touch(String sessionId, Instant now) {
    }
}
