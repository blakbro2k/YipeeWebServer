package asg.games.server.yipeewebserver.net.api;

public record JoinTableRequest(
        String roomId,
        Integer tableNumber,
        boolean createIfMissing
) {}