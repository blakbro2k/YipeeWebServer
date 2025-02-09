package asg.games.server.yipeewebserver.core;

import asg.games.yipee.aspects.Untraced;
import asg.games.yipee.game.Version;
import asg.games.yipee.persistence.Storage;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation for the Yipee game server.
 * Manages the server lifecycle, including startup, fixed-timestep updates, and cleanup.
 */
public class YipeeServerApplication extends ApplicationAdapter {
    private static final Logger logger = LoggerFactory.getLogger(YipeeServerApplication.class);

    // Game server manager instance
    GameServerManager daemon = new GameServerManager();
    private static final String CONST_TITLE = "Yipee! Game Server";
    private int tcpPort = 80; // Default TCP port
    private int udpPort = 54225; // Default UDP port
    private float tickRate = 20.0f; // Default tick rate (20 ticks/sec)
    private static final float DEFAULT_TICK_INTERVAL = 1.0f / 20; // Default tick interval
    private float tickInterval = DEFAULT_TICK_INTERVAL;
    private float accumulator = 0f;

    /**
     * Constructor for YipeeServerApplication.
     */
    public YipeeServerApplication() {}

    /**
     * Sets up the TCP Port, the UDP Port and the TickRate
     * @param tcpPort
     * @param udpPort
     * @param tickRate
     * @param yipeeGameService
     */
    public void setConfiguration(int tcpPort, int udpPort, float tickRate, Storage yipeeGameService) {
        logger.info("{} Build {}", CONST_TITLE, Version.printVersion());
        logger.info("Setting up server to listen on the following ports: TCP[{}], UDP[{}], TICKRATE[{}]", tcpPort, udpPort, tickRate);
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.tickRate = tickRate;
        this.tickInterval = 1.0f / tickRate;
        daemon.setDBService(yipeeGameService);
        logger.debug("tick rate interval = {}.", tickInterval);
    }

    /**
     * Initializes the server, binding it to the specified TCP and UDP ports.
     * Logs any startup errors and rethrows them as GdxRuntimeException.
     */
    public void create() {
        Gdx.graphics.setForegroundFPS(60);
        logger.info("Starting server on tcp port [{}] and udp port [{}], with tick rate [{} ticks/sec]...", tcpPort, udpPort, tickRate);
        try {
            daemon.setUpServer(tcpPort, udpPort);
        } catch (IOException e) {
            logger.error("Error creating server thread.", e);
            throw new GdxRuntimeException("Error creating server thread.", e);
        }
    }

    /**
     * Updates the server logic in fixed-timestep intervals.
     * Captures exceptions during the update process and logs them.
     */
    @Override
    @Untraced
    public void render() {
        logger.trace("Enter render()");
        try {
            float delta = Gdx.graphics.getDeltaTime();
            accumulator += delta;
            logger.trace("delta={}", delta);
            logger.trace("accumulator={}", accumulator);
            logger.trace("tickInterval={}", tickInterval);

            // Run game logic in fixed tick intervals
            while (accumulator >= tickInterval) {
                daemon.update(tickInterval);
                accumulator -= tickInterval;
                logger.trace("accumulator={}", accumulator);
            }
        } catch (Exception e) {
            logger.error("Error updating server thread.", e);
            throw new GdxRuntimeException("Error updating server thread.", e);
        }
        logger.trace("Exit render()");
    }

    /**
     * Cleans up resources when the application exits.
     */
    @Override
    public void dispose() {
        logger.trace("enter dispose()");
        daemon.dispose();
        logger.trace("exit dispose()");
    }

    @Override
    public void pause() {
        logger.trace("enter pause(), currently not supported");
    }
}
