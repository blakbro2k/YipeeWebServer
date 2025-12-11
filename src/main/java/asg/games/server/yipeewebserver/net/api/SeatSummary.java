package asg.games.server.yipeewebserver.net.api;

public record SeatSummary(
        String playerId,
        String playerName,
        String seatId,
        int seatNumber,
        boolean ready,
        boolean occupied
) {}