package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.Version;
import asg.games.server.yipeewebserver.aspects.Untraced;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.yipee.core.persistence.Storage;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation for the Yipee game server.
 * Manages the server lifecycle, including startup, fixed-timestep updates, and cleanup.
 */
@Slf4j
public class YipeeServerApplication extends ApplicationAdapter {
    private final ApplicationContext appContext;
    public static final String CONST_SERVICE_NAME = "YipeeGameServer";

    // Game server manager instance
    ServerManager daemon;
    private static final String CONST_TITLE = "Yipee! Game Server";
    private static final int CONST_SRV_FPS = 60;
    private static final float CONST_SRV_TICK_INTERVAL = 1.0f / 20; // Default tick interval
    private int tcpPort = 80; // Default TCP port
    private int udpPort = 54225; // Default UDP port
    private float tickRate = 20.0f; // Default tick rate (20 ticks/sec)
    private float tickInterval = CONST_SRV_TICK_INTERVAL;
    private float accumulator = 0f;

    /**
     * Constructor for YipeeServerApplication.
     */
    public YipeeServerApplication(ApplicationContext appContext,
                                  YipeePacketHandler yipeePacketHandler,
                                  GameContextFactory gameContextFactory) {
        this.appContext = appContext;
        daemon = new ServerManager(yipeePacketHandler, gameContextFactory);
    }

    /**
     * Sets up the TCP Port, the UDP Port and the TickRate
     * @param tcpPort
     * @param udpPort
     * @param tickRate
     * @param yipeeGameJPAService
     */
    public void setConfiguration(int tcpPort, int udpPort, float tickRate, Storage yipeeGameJPAService) {
        log.info("{} Build {}", CONST_TITLE, Version.printVersion());
        log.info("Setting up server to listen on the following ports: TCP[{}], UDP[{}], TICKRATE[{}]", tcpPort, udpPort, tickRate);
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.tickRate = tickRate;
        this.tickInterval = 1.0f / tickRate;

        log.info("Setting up Database Services....");
        daemon.setDBService(yipeeGameJPAService);
        log.debug("tick rate interval = {}.", tickInterval);
    }

    public void setForgroundFPS(int fps) {
        Gdx.graphics.setForegroundFPS(fps);
    }

    /**
     * Initializes the server, binding it to the specified TCP and UDP ports.
     * Logs any startup errors and rethrows them as GdxRuntimeException.
     */
    public void create() {
        log.info("Setting FPS to {}", CONST_SRV_FPS);
        setForgroundFPS(CONST_SRV_FPS);
        log.info("Starting server on tcp port [{}] and udp port [{}], with tick rate [{} ticks/sec]...", tcpPort, udpPort, tickRate);
        try {
            log.info("Setting server...");
            daemon.setUpKryoServer(tcpPort, udpPort);
        } catch (IOException e) {
            log.error("Error creating server thread. Cannot proceed with server set up.", e);
            appShutDown(-10);
        } catch (ParserConfigurationException | SAXException e) {
            log.error("There was an issue creating the server daemon.",e);
            appShutDown(-11);
        }
    }

    /**
     * Updates the server logic in fixed-timestep intervals.
     * Captures exceptions during the update process and logs them.
     */
    @Override
    @Untraced
    public void render() {
        try {
            float delta = Gdx.graphics.getDeltaTime();
            accumulator += delta;
            log.trace("delta={}", delta);
            log.trace("accumulator={}", accumulator);
            log.trace("tickInterval={}", tickInterval);

            // Run game logic in fixed tick intervals
            while (accumulator >= tickInterval) {
                daemon.update(tickInterval);
                accumulator -= tickInterval;
                log.trace("accumulator={}", accumulator);
            }
        } catch (Exception e) {
            log.error("Error updating server thread.", e);
            throw new GdxRuntimeException("Error updating server thread.", e);
        }
    }

    /**
     * Cleans up resources when the application exits.
     */
    @Override
    public void dispose() {
        daemon.dispose();
    }

    @Override
    public void pause() {
        log.warn("enter pause(), currently not supported");
    }

    private void appShutDown(int returnCode) {
        SpringApplication.exit(appContext, () -> returnCode);
    }
}
