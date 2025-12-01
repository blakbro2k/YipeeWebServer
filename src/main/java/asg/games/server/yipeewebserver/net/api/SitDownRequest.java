package asg.games.server.yipeewebserver.net.api;

public record SitDownRequest(
        String playerId,
        String tableId,
        int seatNumber
) {}
