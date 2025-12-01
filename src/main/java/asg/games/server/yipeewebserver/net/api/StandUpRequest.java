package asg.games.server.yipeewebserver.net.api;

public record StandUpRequest(
        String playerId,
        String tableId
) {}