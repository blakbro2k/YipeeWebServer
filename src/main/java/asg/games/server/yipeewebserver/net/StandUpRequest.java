package asg.games.server.yipeewebserver.net;

public record StandUpRequest(
        String playerId,
        String tableId
) {}