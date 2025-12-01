package asg.games.server.yipeewebserver.net.api;

public record LeaveRoomRequest(
        String playerId,
        String roomId
) {}