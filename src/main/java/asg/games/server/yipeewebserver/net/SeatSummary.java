package asg.games.server.yipeewebserver.net;

public record SeatSummary(
        String seatId,
        int seatNumber,
        boolean ready,
        boolean occupied
) {}