package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.headless.HeadlessLauncher;
import asg.games.server.yipeewebserver.services.PlayerConnectionCleanupService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShutDownHandler {
    @Autowired
    private HeadlessLauncher launcher;

    @Autowired
    private ApplicationContext appContext;

    @Autowired
    PlayerConnectionCleanupService yipeeCleanUpService;

    @PreDestroy
    public void onShutdown() {
        log.info("Server is shutting down...");
        try {
            log.info("clearing connection sessions...");
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