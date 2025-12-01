package asg.games.server.yipeewebserver.net.api;

public record JoinOrCreateTableRequest(
        String playerId,
        String roomId,
        Integer tableNumber,
        boolean createIfMissing
) {}