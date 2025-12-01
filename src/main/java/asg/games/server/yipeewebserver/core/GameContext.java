package asg.games.server.yipeewebserver.core;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public record GameContext(
        String serviceName,
        String serverId,
        long serverTick,
        String clientId,
        String gameId,      // e.g. ServerGameManager.getGameId()
        String sessionId,   // optional
        long timestampMillis
) {}
