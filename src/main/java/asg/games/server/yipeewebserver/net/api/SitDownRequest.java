package asg.games.server.yipeewebserver.net.api;

public record SitDownRequest(
        String tableId,
        int seatNumber
) {}
