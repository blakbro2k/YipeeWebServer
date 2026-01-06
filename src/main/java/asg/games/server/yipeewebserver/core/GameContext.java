package asg.games.server.yipeewebserver.core;

public record GameContext(
        String serviceName,
        String serverId,
        long serverTick,
        String clientId,
        String gameId,      // e.g. ServerGameManager.getGameId()
        String sessionId,   // optional
        String playerId,
        long timestampMillis
) {}