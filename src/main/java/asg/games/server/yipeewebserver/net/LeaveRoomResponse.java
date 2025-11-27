package asg.games.server.yipeewebserver.net;

public record LeaveRoomResponse(
        String roomId,
        String playerId,
        boolean leftRoom
) {}