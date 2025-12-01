package asg.games.server.yipeewebserver.net.api;

public record LeaveRoomResponse(
        String roomId,
        String playerId,
        boolean leftRoom
) {}