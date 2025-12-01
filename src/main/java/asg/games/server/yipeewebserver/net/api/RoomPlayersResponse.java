package asg.games.server.yipeewebserver.net.api;

public record RoomPlayersResponse(
        String roomId,
        String roomName,
        String loungeName,
        java.util.List<PlayerSummary> players
) {}