package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.core.GameSessionStore;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.session.GameSession;
import asg.games.yipee.net.errors.YipeeSessionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GameSessionService {

    private final SessionService sessionService;       // DB-backed: validates
    private final GameSessionStore gameSessionStore;   // in-memory: bindings
    private final GameSessionTokenService gameSessionTokenService;   // in-memory: bindings

    public GameSession requireGameSession(String sessionId, String clientId) {
        // validates lifetime + ownership (DB)
        PlayerConnectionEntity conn = sessionService.requireSession(sessionId, clientId);

        // in-memory must exist if we expect game binding
        return gameSessionStore.findBySessionId(sessionId)
                .orElseGet(() -> {
                    // Session is valid, but no game binding yet.
                    // Return a minimal GameSession so callers can still access playerId.
                    Instant now = Instant.now();
                    return new GameSession(sessionId, clientId, conn.getPlayer().getId(), null, null, now, now);
                });
    }

    /**
     * Validates the signed game session token and returns a GameSession that is
     * bound to the game/table contained in the token.
     *
     * <p>Preferred for game-scoped endpoints launched from the lobby, since the client
     * does not need to send sessionId/clientId/tableId separately.</p>
     */
    public GameSession requireGameSessionByToken(String gameSessionToken) {
        GameSessionTokenService.GameSessionTokenContext ctx = gameSessionTokenService.requireContext(gameSessionToken);

        // Reuse existing DB validation (ownership + expiry) if you want that extra gate:
        GameSession base = requireGameSession(ctx.sessionId(), ctx.clientId());

        // Optional sanity: ensure token subject matches DB session owner
        if (!base.playerId().equals(ctx.playerId())) {
            throw new YipeeSessionException("Token player does not match session owner.");
        }

        Instant now = Instant.now();
        Instant boundAt = (base.boundAt() != null) ? base.boundAt() : now;

        GameSession bound = new GameSession(
                base.sessionId(),
                base.clientId(),
                base.playerId(),
                ctx.gameId(),
                ctx.tableId(),
                boundAt,
                now
        );

        gameSessionStore.upsert(bound);
        return bound;
    }


    public GameSession bindGame(String sessionId, String clientId, String gameId) {
        PlayerConnectionEntity conn = sessionService.requireSession(sessionId, clientId);
        Instant now = Instant.now();

        GameSession current = gameSessionStore.findBySessionId(sessionId).orElse(null);
        Instant boundAt = (current != null && current.boundAt() != null) ? current.boundAt() : now;

        GameSession gs = new GameSession(sessionId, clientId, conn.getPlayer().getId(), gameId, null, boundAt, now);
        gameSessionStore.upsert(gs);
        return gs;
    }


    public GameSession bindTable(String sessionId, String clientId, String gameId, String tableId) {
        PlayerConnectionEntity conn = sessionService.requireSession(sessionId, clientId);
        Instant now = Instant.now();
        GameSession gs = new GameSession(sessionId, clientId, conn.getPlayer().getId(), gameId, tableId, now, now);
        gameSessionStore.upsert(gs);
        return gs;
    }

    public void clearGameBinding(String sessionId, String clientId) {
        // validate ownership before clearing
        sessionService.requireSession(sessionId, clientId);
        gameSessionStore.clear(sessionId);
    }

    public String requireTableId(GameSession gs) {
        if (gs.tableId() == null || gs.tableId().isBlank()) {
            throw new YipeeSessionException("Session is not bound to a table.");
        }
        return gs.tableId();
    }

    public String requireGameId(GameSession gs) {
        if (gs.gameId() == null || gs.gameId().isBlank()) {
            throw new YipeeSessionException("Session is not bound to a game.");
        }
        return gs.gameId();
    }

    public GameSession touchOrCreateMinimal(String sessionId, String clientId) {
        PlayerConnectionEntity conn = sessionService.requireSession(sessionId, clientId);
        Instant now = Instant.now();

        return gameSessionStore.findBySessionId(sessionId)
                .map(existing -> {
                    gameSessionStore.touch(sessionId, now);
                    return existing;
                })
                .orElseGet(() -> {
                    GameSession gs = new GameSession(
                            sessionId,
                            clientId,
                            conn.getPlayer().getId(),
                            null,
                            null,
                            now,
                            now
                    );
                    gameSessionStore.upsert(gs);
                    return gs;
                });
    }

    public GameSession createMinimal(String sessionId, String clientId, String playerId) {
        Instant now = Instant.now();
        GameSession gs = new GameSession(sessionId, clientId, playerId, null, null, now, now);
        gameSessionStore.upsert(gs);
        return gs;
    }
}
