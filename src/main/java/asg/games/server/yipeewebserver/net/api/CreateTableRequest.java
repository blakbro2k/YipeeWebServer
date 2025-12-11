package asg.games.server.yipeewebserver.net.api;

public record CreateTableRequest(
        String roomId,
        boolean rated,
        boolean soundOn,
        String accessType
) {}