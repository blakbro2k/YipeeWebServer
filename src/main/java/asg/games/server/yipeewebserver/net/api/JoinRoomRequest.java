package asg.games.server.yipeewebserver.net.api;

public record JoinRoomRequest(
        String playerId,
        String roomId
) {}