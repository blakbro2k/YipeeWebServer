package asg.games.server.yipeewebserver.jobs;

import asg.games.server.yipeewebserver.services.PlayerConnectionCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionCleanupJob {

    private final PlayerConnectionCleanupService cleanupService;

    @Scheduled(fixedRate = 60_000) // every minute
    public void runCleanup() {
        cleanupService.cleanupExpiredSessions();
    }
}