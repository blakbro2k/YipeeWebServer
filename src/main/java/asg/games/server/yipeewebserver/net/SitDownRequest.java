package asg.games.server.yipeewebserver.net;

public record SitDownRequest(
        String playerId,
        String tableId,
        int seatNumber
) {}
