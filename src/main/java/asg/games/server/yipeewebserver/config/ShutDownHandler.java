package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.headless.HeadlessLauncher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ShutDownHandler {
    private static final Logger logger = LoggerFactory.getLogger(ShutDownHandler.class);
    @Autowired
    private HeadlessLauncher launcher;

    @Autowired
    private ApplicationContext appContext;

    @PreDestroy
    public void onShutdown() {
        // Your shutdown logic here
        logger.info("Application is shutting down...");
        launcher.shutDown();
    }

    /*
     * Invoke with `0` to indicate no error or different code to indicate
     * abnormal exit. es: shutdownManager.initiateShutdown(0);
     **/
    public void initiateShutdown(int returnCode){
        SpringApplication.exit(appContext, () -> returnCode);
    }
}