package asg.games.server.yipeewebserver.net;

public record LeaveTableResponse(
        String tableId,
        String playerId,
        boolean leftTable,
        boolean wasSeated,
        boolean wasWatcher
) {}