package asg.games.server.yipeewebserver.security;

public record JwtIdentity(String playerId,
                          String username,
                          Integer rating,
                          Integer icon)
{}