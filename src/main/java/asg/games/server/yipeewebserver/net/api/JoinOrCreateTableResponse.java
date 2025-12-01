package asg.games.server.yipeewebserver.net.api;

public record JoinOrCreateTableResponse(
        String roomId,
        String roomName,
        String tableId,
        int tableNumber,
        boolean created,
        boolean rated,
        boolean soundOn,
        java.util.List<SeatSummary> seats
) {}