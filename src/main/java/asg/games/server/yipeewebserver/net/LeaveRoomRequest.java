package asg.games.server.yipeewebserver.net;

public record LeaveRoomRequest(
        String playerId,
        String roomId
) {}