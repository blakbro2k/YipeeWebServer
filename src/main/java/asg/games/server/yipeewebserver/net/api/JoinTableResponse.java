package asg.games.server.yipeewebserver.net.api;

public record JoinTableResponse(
        String roomId,
        String roomName,
        String tableId,
        String playerId
) {}