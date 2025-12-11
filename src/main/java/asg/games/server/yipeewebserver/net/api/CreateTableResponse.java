package asg.games.server.yipeewebserver.net.api;

public record CreateTableResponse(
        String roomId,
        String roomName,
        String tableId,
        int tableNumber,
        String playerId,
        boolean created
) {}