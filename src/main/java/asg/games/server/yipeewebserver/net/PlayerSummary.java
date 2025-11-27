package asg.games.server.yipeewebserver.net;


public record PlayerSummary(
        String playerId,
        String name,
        int icon,
        int rating
) {}
