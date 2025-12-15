package asg.games.server.yipeewebserver.net.api;

import java.time.Instant;

public record GameWhoAmIResponse(
        String playerId,
        String clientId,
        String sessionId,
        String gameId,
        String tableId,
        Instant expiresAt
) {}