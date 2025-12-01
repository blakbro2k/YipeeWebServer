package asg.games.server.yipeewebserver.net.api;

public record TableSummary(
        String tableId,
        int tableNumber,
        boolean rated,
        boolean soundOn,
        int watcherCount
) {}