package asg.games.server.yipeewebserver.net;

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