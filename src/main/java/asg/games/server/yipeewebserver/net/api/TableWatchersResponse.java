package asg.games.server.yipeewebserver.net.api;

public record TableWatchersResponse(
        String tableId,
        int watcherCount,
        java.util.List<PlayerSummary> watchers
) {}