package asg.games.server.yipeewebserver.net.api;

public record TableSummary(
        String tableId,
        int tableNumber,
        String accessType,
        boolean created,
        boolean rated,
        boolean soundOn,
        int watcherCount
) {}