package asg.games.server.yipeewebserver.net.api;

public record RoomSummary(
        String roomId,
        String name,
        String loungeName,
        int playerCount,
        int tableCount
) {}