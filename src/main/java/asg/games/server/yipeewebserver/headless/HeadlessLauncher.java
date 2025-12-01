package asg.games.server.yipeewebserver.headless;

import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.core.YipeeServerApplication;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Launches the LibGDX headless game server ( {@link YipeeServerApplication} ) inside a Spring context.
 * <p>
 * This component is responsible for:
 * <ul>
 *     <li>Creating and configuring a {@link HeadlessApplication} instance.</li>
 *     <li>Wiring the {@link YipeeServerApplication} with ports, tick rate, and storage.</li>
 *     <li>Exposing a {@link #shutDown()} hook to cleanly stop the headless server.</li>
 * </ul>
 * It is designed to be used as a Spring-managed bean, but also provides a
 * {@code main} entry point for manual launching if needed.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeadlessLauncher {
    /**
     * The underlying LibGDX headless application instance.
     * <p>
     * Marked {@code volatile} to ensure safe publication across threads.
     * </p>
     */
    private static volatile HeadlessApplication app;

    /**
     * Tracks whether the headless server has already been started.
     * <p>
     * Used to prevent accidental duplicate launches.
     * </p>
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Log level used for the underlying {@link HeadlessApplication}.
     * <p>
     * Note: injecting into static fields is not supported in all Spring configurations.
     * Consider refactoring this to a non-static field if needed.
     * </p>
     */
    @Value("${gameserver.logLevel}")
    private static int serverLogLevel;

    private final YipeePacketHandler yipeePacketHandler;
    private final GameContextFactory gameContextFactory;
    private final ApplicationContext appContext;
    private final YipeeGameJPAServiceImpl yipeeGameJPAService;

    /**
     * Optional standalone entry point for launching the headless server without Spring.
     *
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        log.info("Main launcher args: {}", Arrays.toString(args));
        // createApplication(new YipeeServerApplication(null));
    }

    /**
     * Launches the headless Yipee game server.
     * <p>
     * This method is intended to be called once during application startup. Subsequent
     * calls are ignored to avoid creating multiple {@link HeadlessApplication} instances.
     * </p>
     *
     * @param tcpPort           TCP port for the game server.
     * @param udpPort           UDP port for the game server.
     * @param tickRate          Tick rate for the game loop (updates per second).
     */
    public void launch(int tcpPort, int udpPort, float tickRate) {
        if (!started.compareAndSet(false, true)) {
            log.debug("HeadlessLauncher already started; ignoring duplicate launch.");
            return;
        }
        log.info("Launching headless server: tcp={} udp={} tickRate={}", tcpPort, udpPort, tickRate);

        // Create YipeeServerApplication and pass configuration
        YipeeServerApplication yipeeServerApplication = new YipeeServerApplication(appContext, yipeePacketHandler, gameContextFactory);
        yipeeServerApplication.setConfiguration(tcpPort, udpPort, tickRate, yipeeGameJPAService);

        // Start the LibGDX application with custom configuration
        createApplication(yipeeServerApplication);
    }

    /**
     * Creates the underlying {@link HeadlessApplication} using the provided
     * {@link YipeeServerApplication} and the default configuration.
     *
     * @param yipeeServerApplication the game server application to run headlessly.
     */
    private static void createApplication(YipeeServerApplication yipeeServerApplication) {
        // Note: you can use a custom ApplicationListener implementation for the headless project instead of YipeeServerApplication.
        app = new HeadlessApplication(yipeeServerApplication, getDefaultConfiguration());
        app.setLogLevel(serverLogLevel);
    }

    /**
     * Builds the default {@link HeadlessApplicationConfiguration} for the Yipee server.
     * <p>
     * By default, {@code updatesPerSecond} is set to {@code -1}, which means
     * {@link YipeeServerApplication#render()} is never called and the application
     * must drive its own update loop internally.
     * </p>
     *
     * @return a {@link HeadlessApplicationConfiguration} instance.
     */
    private static HeadlessApplicationConfiguration getDefaultConfiguration() {
        HeadlessApplicationConfiguration configuration = new HeadlessApplicationConfiguration();
        configuration.updatesPerSecond = -1; // When this value is negative, YipeeServerApplication#render() is never called.
        //// If the above line doesn't compile, it is probably because the project libGDX version is older.
        //// In that case, uncomment and use the below line.
        //configuration.renderInterval = -1f; // When this value is negative, YipeeServerApplication#render() is never called.
        return configuration;
    }

    /**
     * Gracefully shuts down the headless LibGDX application, if running.
     * <p>
     * This method:
     * <ol>
     *     <li>Prevents re-entrance via the {@link #started} flag.</li>
     *     <li>Invokes {@link ApplicationListener#pause()}, {@link ApplicationListener#dispose()},</li>
     *     <li>Optionally calls {@link AutoCloseable#close()} if the listener implements it.</li>
     *     <li>Calls {@link HeadlessApplication#exit()} on the underlying app.</li>
     * </ol>
     * Any exceptions during shutdown are logged but not rethrown.
     * </p>
     */
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