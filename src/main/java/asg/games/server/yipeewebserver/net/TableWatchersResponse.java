package asg.games.server.yipeewebserver.net;

public record TableWatchersResponse(
        String tableId,
        int watcherCount,
        java.util.List<PlayerSummary> watchers
) {}