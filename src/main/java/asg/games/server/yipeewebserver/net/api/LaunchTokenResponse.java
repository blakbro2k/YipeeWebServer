package asg.games.server.yipeewebserver.net.api;

import java.time.Instant;

public record LaunchTokenResponse(String launchToken, Instant expiresAt, String wsUrl) {}
