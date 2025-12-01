package asg.games.server.yipeewebserver.net.api;

public record SitDownResponse(
        String roomId,
        String roomName,
        String tableId,
        int tableNumber,
        String seatId,
        int seatNumber,
        boolean seatReady,
        boolean occupied
) {}