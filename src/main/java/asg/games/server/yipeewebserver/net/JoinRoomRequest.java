package asg.games.server.yipeewebserver.net;

public record JoinRoomRequest(
        String playerId,
        String roomId
) {}