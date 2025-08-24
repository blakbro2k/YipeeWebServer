package asg.games.server.yipeewebserver.headless;

import asg.games.server.yipeewebserver.core.YipeeServerApplication;
import asg.games.yipee.core.persistence.Storage;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
/** Launches the headless application. Can be converted into a utilities project or a server application. */
public class HeadlessLauncher {
    private static final Logger logger = LoggerFactory.getLogger(HeadlessLauncher.class);
    private static volatile HeadlessApplication app;
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Value("${gameserver.logLevel}")
    private static int serverLogLevel;

    public static void main(String[] args) {
        logger.info("Main launcher args: {}", Arrays.toString(args));
        createApplication(new YipeeServerApplication());
    }

    /**
     * Launches the headless application with the specified TCP port, UDP port, and tick rate.
     * @param tcpPort  TCP port for the server.
     * @param udpPort  UDP port for the server.
     * @param tickRate Tick rate for the game loop.
     * @param yipeeGameJPAService Storage Service Object
     */
    public void launch(int tcpPort, int udpPort, float tickRate, Storage yipeeGameJPAService) {
        if (!started.compareAndSet(false, true)) {
            log.debug("HeadlessLauncher already started; ignoring duplicate launch.");
            return;
        }
        log.info("Launching headless server: tcp={} udp={} tickRate={}", tcpPort, udpPort, tickRate);

        // Create YipeeServerApplication and pass configuration
        YipeeServerApplication yipeeServerApplication = new YipeeServerApplication();
        yipeeServerApplication.setConfiguration(tcpPort, udpPort, tickRate, yipeeGameJPAService);

        // Start the LibGDX application with custom configuration
        createApplication(yipeeServerApplication);
    }

    private static void createApplication(YipeeServerApplication yipeeServerApplication) {
        // Note: you can use a custom ApplicationListener implementation for the headless project instead of YipeeServerApplication.
        app = new HeadlessApplication(yipeeServerApplication, getDefaultConfiguration());
        app.setLogLevel(serverLogLevel);
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
        if (!started.compareAndSet(true, false)) {
            return; // not running
        }
        if (app == null) return;

        try {
            ApplicationListener listener = app.getApplicationListener();
            if (listener != null) {
                // Let the listener stop its own tick/executors and close sockets
                try { listener.pause(); } catch (Throwable ignored) {}
                try {
                    if (listener instanceof AutoCloseable ac) ac.close(); // if you add it
                } catch (Throwable ignored) {}
                try { listener.dispose(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            log.warn("Error while shutting down headless application", t);
        } finally {
            try { app.exit(); } catch (Throwable ignored) {}
            log.info("HeadlessLauncher stopped.");
        }
    }
}