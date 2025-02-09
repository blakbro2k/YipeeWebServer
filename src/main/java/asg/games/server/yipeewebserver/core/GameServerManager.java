package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.data.DTOObject;
import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.yipee.game.GameManager;
import asg.games.yipee.net.ConnectionRequest;
import asg.games.yipee.net.ConnectionResponse;
import asg.games.yipee.net.DisconnectRequest;
import asg.games.yipee.net.StartGameRequest;
import asg.games.yipee.objects.YipeePlayer;
import asg.games.yipee.persistence.Storage;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import asg.games.yipee.net.PacketRegistrar;

/**
 * Manages the game server, including networking, player connections, and game state updates.
 */
public class GameServerManager implements Disposable {
    private static final Logger logger = LoggerFactory.getLogger(GameServerManager.class);
    private static final String ARG_USER_CONNECT_NAME_TAG = "#CONNECTION";
    private static final String ARG_NO_PLAYER_NAME_TAG = "_no_player_name";

    // The KryoNet server instance
    Server server = new Server();

    // Unique identifier for the server instance
    String serverId = UUID.randomUUID().toString();
    private final ConcurrentHashMap<Long, Thread> gameThreads = new ConcurrentHashMap<>();
    private final ScheduledExecutorService gameExecutor = Executors.newScheduledThreadPool(8); // Adjust thread pool size
    private final ConcurrentHashMap<Long, GameManager> gameManagers = new ConcurrentHashMap<>();
    private Storage storageAdapter;

    /**
     * Constructor for GameServerManager.
     */
    public GameServerManager() {
    }

    /**
     * Broadcasts the current game state to all connected clients.
     * This method should be called periodically during the game loop.
     */
    public void broadcastGameState() {
    }

    /**
     * Checks whether the game end conditions are met.
     * This can involve checking scores, timers, or other game-specific logic.
     */
    public void checkGameEndConditions() {
    }

    /**
     *
     * @param yipeeGameServices
     */
    public void setDBService(Storage yipeeGameServices) {
        storageAdapter = yipeeGameServices;
    }

    /**
     * Sets up and starts the game server, binding to the specified TCP and UDP ports.
     *
     * @param tcpPort The port for TCP connections.
     * @param udpPort The port for UDP connections.
     * @throws IOException if there is an error during server binding.
     */
    public void setUpServer(int tcpPort, int udpPort) throws IOException {
        server.start(); // Start the server
        // Register all necessary packet classes for serialization
        PacketRegistrar.registerPackets(server.getKryo());

        server.bind(tcpPort, udpPort); // Bind the server to the given ports



        // Add a listener to handle incoming requests
        server.addListener(new Listener.ThreadedListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (connection.getRemoteAddressTCP() == null) {
                    logger.warn("Ignoring UDP packet from unknown client: " + connection);
                    return;
                }

                if (object instanceof ConnectionRequest request) {
                    handleConnectionRequest(connection, request);
                } else if (object instanceof StartGameRequest request) {
                    handleStartGameRequest(request);
                } else if (object instanceof DisconnectRequest request) {
                    handleDisconnectRequest(connection, request);
                }
            }
        }));
    }

    /**
     * Saves a {@code DTOObject} to the persistence storage
     *
     * @param object
     */
    private void persistObject(DTOObject object) {
        if(storageAdapter != null) {
            storageAdapter.saveObject(object);
        }
    }

    /**
     * Deletes a {@code DTOObject} from the persistence storage
     *
     * @param object
     */
    private void dePersistObject(DTOObject object) {
        if(storageAdapter != null) {
            storageAdapter.deleteObject(object);
        }
    }

    /**
     * Deletes a {@code DTOObject} from the persistence storage
     *
     * @param object
     */
    private <T extends DTOObject> T getPersistObject(Class<T> clazz, DTOObject object) {
        T obj = null;
        if(storageAdapter != null) {
            try {
                obj = getObjectByName(clazz, object.getName());
                if(obj == null) {
                    obj = getObjectById(clazz, object.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to getPersistObject Object:[{}] from database.", obj);
            }
        }
        return obj;
    }

    private <T extends DTOObject> T getObjectByName(Class<T> clazz, String name) throws Exception {
        return storageAdapter.getObjectByName(clazz, name);
    }

    private <T extends DTOObject> T getObjectById(Class<T> clazz, String id) throws Exception {
        return storageAdapter.getObjectById(clazz, id);
    }

    private String buildPlayerConnectionName(YipeePlayer player) {
        String name;
        if(player != null) {
            name = player.getName();
        } else {
            name = ARG_NO_PLAYER_NAME_TAG;
        }
        return name + ARG_USER_CONNECT_NAME_TAG;
    }

    /**
     * Handles a connection request from a client.
     *
     * @param connection The client connection.
     * @param request    The connection request object.
     */
    private void handleConnectionRequest(Connection connection, ConnectionRequest request) {
        logger.info("Received ConnectionRequest: {}", request);

        if(request != null) {
            YipeePlayer testP = request.getPlayer();
            PlayerConnectionDTO playerConnectDB = new PlayerConnectionDTO();
            playerConnectDB.setClientId(request.getClientId());
            playerConnectDB.setConnected(true);
            playerConnectDB.setPlayer(testP);
            playerConnectDB.setTimeStamp(TimeUtils.millis());
            playerConnectDB.setSessionId(UUID.randomUUID().toString()+TimeUtils.millis());
            playerConnectDB.setName(buildPlayerConnectionName(testP));
            logger.error("playerConnectDB: {}", playerConnectDB);
            persistObject(playerConnectDB);

            // Send connection response without starting the game
            ConnectionResponse response = new ConnectionResponse();
            response.setServerId(serverId);
            response.setConnected(true);
            response.setPlayer(testP);
            response.setTimeStamp(TimeUtils.millis());
            logger.error("response: {}", response);
            connection.sendTCP(response);
        }
    }

    /**
     * Handles a disconnect request from a client.
     *
     * @param connection The client connection.
     * @param request    The disconnect request object.
     */
    private void handleDisconnectRequest(Connection connection, DisconnectRequest request) {
        String clientId = request.getClientId();
        if (gameManagers.remove(clientId) != null) {
            logger.debug("Stopped GameManager for clientId={}", clientId);
        }

        if (gameThreads.containsKey(clientId)) {
            // Remove the GameManager for the client
            gameThreads.get(clientId).interrupt();
            gameThreads.remove(clientId);
        }

        YipeePlayer player = request.getPlayer();
        String playerName = buildPlayerConnectionName(player);
        PlayerConnectionDTO user = null;
        try {
            user = getObjectByName(PlayerConnectionDTO.class, playerName);
            dePersistObject(user);
        } catch (Exception e) {
            logger.error("Failed to get Object:[{}] from database.", playerName);
        }

        // Send disconnection response
        ConnectionResponse response = new ConnectionResponse();
        response.setServerId(serverId);
        response.setConnected(false);
        response.setPlayer(player);
        response.setTimeStamp(TimeUtils.millis());
        connection.sendTCP(response);
    }

    private void handleStartGameRequest(StartGameRequest request) {
        long clientId = request.getClientId();

        gameManagers.computeIfAbsent(clientId, id -> {
            GameManager gameManager = new GameManager();
            startGameLoop(clientId, gameManager);  // Start the loop directly
            logger.debug("Started GameManager for clientId={}", clientId);
            return gameManager;
        });
    }

    private void startGameLoop(long clientId, GameManager gameManager) {
        Runnable gameLoopTask = () -> gameManager.gameLoopTick(1 / 60f);
        gameExecutor.scheduleAtFixedRate(gameLoopTask, 0, 16, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the game server logic, such as broadcasting state or handling timeouts.
     *
     * @param deltaTime The time since the last update.
     */
    public void update(float deltaTime) {
        // Update all GameManagers globally
        gameManagers.values().forEach(gameManager -> gameManager.gameLoopTick(deltaTime));
        checkGameEndConditions();
        broadcastGameState();
    }

    /**
     * Disposes of server resources gracefully.
     */
    @Override
    public void dispose() {
        try {
            logger.trace("Entering Game Dispose");
            //server.dispose(); // Dispose of the server resources
            logger.info("Shutting down GameServerManager...");

            // Stop all active game threads
            gameThreads.values().forEach(Thread::interrupt);
            gameThreads.clear();

            // Stop the ScheduledExecutorService
            gameExecutor.shutdown();
            if (!gameExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                gameExecutor.shutdownNow();
            }

            // Dispose of the server resources
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            logger.error("Error while shutting down GameServerManager", e);
            throw new RuntimeException("Error while shutting down GameServerManager", e);
        } finally {
            logger.trace("Exit Game Dispose");
        }
    }
}