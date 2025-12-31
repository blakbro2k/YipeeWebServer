package asg.games.server.yipeewebserver.session;

import java.time.Instant;

public record GameSession(
        String sessionId,
        String clientId,
        String playerId,
        String gameId,
        String tableId,
        Instant boundAt,
        Instant lastSeen
) {}


