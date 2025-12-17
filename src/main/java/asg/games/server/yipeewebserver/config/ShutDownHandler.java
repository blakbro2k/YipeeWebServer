package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.headless.HeadlessLauncher;
import asg.games.server.yipeewebserver.services.YipeeCleanupService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShutDownHandler {

    private final HeadlessLauncher launcher;
    private final ApplicationContext appContext;
    private final YipeeCleanupService yipeeCleanUpService;

    @PreDestroy
    public void onShutdown() {
        log.warn("Server is shutting down...");

        // 1) Stop game loop / network writers first (best practice)
        try {
            log.warn("shutdown: stopping headless launcher...");
            launcher.shutDown();
            log.warn("shutdown: headless launcher stopped.");
        } catch (Exception e) {
            log.error("shutdown: error while stopping headless launcher", e);
        }

        // 2) Delete ephemeral connection/session rows
        try {
            log.warn("shutdown: clearing connection sessions...");
            yipeeCleanUpService.deleteAllConnections();
            log.warn("shutdown: connection sessions cleared.");
        } catch (Exception e) {
            log.error("shutdown: error while clearing connection sessions", e);
        }

        // 3) Forced purge of all tables (UNCONDITIONAL — does NOT rely on occupancy counters)
        try {
            log.warn("shutdown: FORCE PURGE all tables/seats/watchers/activity...");
            yipeeCleanUpService.forceDeleteAllTables();
            log.warn("shutdown: FORCE PURGE complete.");
        } catch (Exception e) {
            log.error("shutdown: error while force purging tables", e);
        }

        log.warn("shutdown: complete.");
    }

    /*
     * Invoke with `0` to indicate no error or different code to indicate
     * abnormal exit. e.g. shutdownManager.initiateShutdown(0);
     */
    public void initiateShutdown(int returnCode) {
        SpringApplication.exit(appContext, () -> returnCode);
    }
}