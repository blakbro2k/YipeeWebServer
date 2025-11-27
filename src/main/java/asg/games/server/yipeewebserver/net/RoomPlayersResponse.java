package asg.games.server.yipeewebserver.net;

public record RoomPlayersResponse(
        String roomId,
        String roomName,
        String loungeName,
        java.util.List<PlayerSummary> players
) {}