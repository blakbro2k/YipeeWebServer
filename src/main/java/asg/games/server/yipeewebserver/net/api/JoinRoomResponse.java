package asg.games.server.yipeewebserver.net.api;

public record JoinRoomResponse(
        String roomId,
        String roomName,
        String loungeName,
        java.util.List<TableSummary> tables
) {}