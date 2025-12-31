package asg.games.server.yipeewebserver.net.api;

public record SeatSummary(
        String seatId,
        int seatNumber,
        boolean seatReady,
        boolean occupied,
        String playerId,
        String playerName
) {}