package asg.games.server.yipeewebserver.net;

public record RoomSummary(
        String roomId,
        String name,
        String loungeName,
        int playerCount,
        int tableCount
) {}