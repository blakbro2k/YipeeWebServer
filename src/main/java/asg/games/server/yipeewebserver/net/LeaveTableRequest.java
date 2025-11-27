package asg.games.server.yipeewebserver.net;

public record LeaveTableRequest(
        String playerId,
        String tableId
) {}