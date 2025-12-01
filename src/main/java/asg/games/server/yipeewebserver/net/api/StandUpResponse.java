package asg.games.server.yipeewebserver.net.api;

public record StandUpResponse(
        String roomId,
        String roomName,
        String tableId,
        int tableNumber,
        String seatId,
        int seatNumber,
        boolean success
) {}