package asg.games.server.yipeewebserver.headless;

import asg.games.server.yipeewebserver.config.ShutDownHandler;
import asg.games.server.yipeewebserver.core.YipeeServerApplication;
import asg.games.server.yipeewebserver.services.YipeeGameServices;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.persistence.Storage;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
/** Launches the headless application. Can be converted into a utilities project or a server application. */
public class HeadlessLauncher {
    private static final Logger logger = LoggerFactory.getLogger(HeadlessLauncher.class);
    private HeadlessApplication app;

    public static void main(String[] args) {
        System.out.println("Main launcher args: " + Arrays.toString(args));
        createApplication(new YipeeServerApplication());
    }

    /**
     * Launches the headless application with the specified TCP port, UDP port, and tick rate.
     * @param tcpPort  TCP port for the server.
     * @param udpPort  UDP port for the server.
     * @param tickRate Tick rate for the game loop.
     * @param yipeeGameService
     */
    public void launch(int tcpPort, int udpPort, float tickRate, Storage yipeeGameService) {
        logger.debug("launcher tcpPort: {}", tcpPort);
        logger.debug("launcher udpPort: ", udpPort);
        logger.debug("launcher tickRate: ", tickRate);

        // Create YipeeServerApplication and pass configuration
        YipeeServerApplication yipeeServerApplication = new YipeeServerApplication();
        yipeeServerApplication.setConfiguration(tcpPort, udpPort, tickRate, yipeeGameService);

        // Start the LibGDX application with custom configuration
        createApplication(yipeeServerApplication);
    }

    private static void createApplication(YipeeServerApplication yipeeServerApplication) {
        // Note: you can use a custom ApplicationListener implementation for the headless project instead of YipeeServerApplication.
        HeadlessApplication app = new HeadlessApplication(yipeeServerApplication, getDefaultConfiguration());
        app.setLogLevel(3);
    }

    private static HeadlessApplicationConfiguration getDefaultConfiguration() {
        HeadlessApplicationConfiguration configuration = new HeadlessApplicationConfiguration();
        configuration.updatesPerSecond = -1; // When this value is negative, YipeeServerApplication#render() is never called.
        //// If the above line doesn't compile, it is probably because the project libGDX version is older.
        //// In that case, uncomment and use the below line.
        //configuration.renderInterval = -1f; // When this value is negative, YipeeServerApplication#render() is never called.
        return configuration;
    }

    public void shutDown() {
        Gdx.app.getApplicationListener().pause();
        Gdx.app.getApplicationListener().dispose();
    }
}