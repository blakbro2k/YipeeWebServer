package asg.games.server.yipeewebserver.net.api;

public record SeatDetailSummary(
        String seatId,
        int seatNumber,
        boolean seatReady,
        boolean occupied,
        String playerId,
        String playerName,
        Integer playerIcon,
        Integer playerRating
) {}