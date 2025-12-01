package asg.games.server.yipeewebserver.net.api;

public record LeaveTableRequest(
        String playerId,
        String tableId
) {}