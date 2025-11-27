package asg.games.server.yipeewebserver.net;

public record JoinRoomResponse(
        String roomId,
        String roomName,
        String loungeName,
        java.util.List<TableSummary> tables
) {}