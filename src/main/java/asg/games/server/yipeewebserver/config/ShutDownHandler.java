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
        log.info("Server is shutting down...");
        try {
            log.info("clearing connection sessions...");
            // Only clear ephemeral connection/session rows
            yipeeCleanUpService.deleteAllConnections();

            log.info("clearing all talbes...");
            // Only clear ephemeral connection/session rows
            yipeeCleanUpService.deleteAllConnections();

            // If you REALLY want to clear identities too, uncomment this:
            // yipeeGameService.clearAllPlayerIdentities();
            log.info("cleanup complete.");

            // Your shutdown logic here
            launcher.shutDown();
        } catch (Exception e) {
            log.error("YipeeShutdownCleanup: error while cleaning up session data", e);
        }
    }

    /*
     * Invoke with `0` to indicate no error or different code to indicate
     * abnormal exit. es: shutdownManager.initiateShutdown(0);
     **/
    public void initiateShutdown(int returnCode){
        SpringApplication.exit(appContext, () -> returnCode);
    }
}