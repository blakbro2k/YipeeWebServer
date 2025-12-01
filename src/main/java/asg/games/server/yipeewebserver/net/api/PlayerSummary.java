package asg.games.server.yipeewebserver.net.api;


public record PlayerSummary(
        String playerId,
        String name,
        int icon,
        int rating
) {}
