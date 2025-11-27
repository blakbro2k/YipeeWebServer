package asg.games.server.yipeewebserver.net;

public record TableSummary(
        String tableId,
        int tableNumber,
        boolean rated,
        boolean soundOn,
        int watcherCount
) {}