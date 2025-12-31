package asg.games.server.yipeewebserver.net.api;

public record SeatDetailSummary(
        String seatId,
        int seatNumber,
        boolean seatReady,
        boolean occupied,
        PlayerSummary playerSummary
) {}