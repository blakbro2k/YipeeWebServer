package asg.games.server.yipeewebserver.net;

public record JoinOrCreateTableRequest(
        String playerId,
        String roomId,
        Integer tableNumber,
        boolean createIfMissing
) {}