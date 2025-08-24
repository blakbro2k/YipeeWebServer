package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.data.DTOObject;
import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.server.yipeewebserver.tools.NetUtil;
import asg.games.yipee.core.net.PacketRegistrar;
import asg.games.yipee.core.objects.YipeeKeyMap;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.persistence.Storage;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import asg.games.yipee.net.packets.DisconnectRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
import asg.games.yipee.net.packets.TableStateBroadcastResponse;
import asg.games.yipee.net.packets.TableStateUpdateRequest;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the game server, including networking, player connections, and game state updates.
 */
public class ServerManager implements Disposable {
    private static final Logger logger = LoggerFactory.getLogger(ServerManager.class);
    private static final String ARG_USER_CONNECT_NAME_TAG = "#CONNECTION";
    private static final String ARG_NO_PLAYER_NAME_TAG = "_no_player_name";

    // The KryoNet server instance
    Server server = new Server();

    // Unique identifier for the server instance
    String serverId = UUID.randomUUID().toString();
    private int currentTick = 0;
    private final ConcurrentHashMap<Long, Thread> gameThreads = new ConcurrentHashMap<>();
    private final ScheduledExecutorService gameExecutor = Executors.newScheduledThreadPool(8); // Adjust thread pool size
    private final ConcurrentHashMap<Long, ServerGameManager> gameManagers = new ConcurrentHashMap<>();
    Map<Long, List<Connection>> connectionsPerGame = new ConcurrentHashMap<>();
    private Storage storageAdapter;

    /**
     * Constructor for GameServerManager.
     */
    public ServerManager() {
    }

    /**
     * Broadcasts the current game state to all connected clients.
     * This method should be called periodically during the game loop.
     */
    public void broadcastGameState() {
        for (Map.Entry<Long, ServerGameManager> entry : gameManagers.entrySet()) {
            long gameId = entry.getKey();
            ServerGameManager manager = entry.getValue();

            int currentTick = getCurrentTick(); // Track or increment this elsewhere
            TableStateBroadcastResponse tickPacket = new TableStateBroadcastResponse(currentTick, manager.getLatestGameBoardStates());

            sendToAllPlayersInGame(gameId, tickPacket);
        }
    }

    private int getCurrentTick() {
        return currentTick;
    }

    private void incrementCurrentTick() {
        currentTick++;
        if(currentTick > 60) {
            currentTick = 0;
        }
    }

    private void sendToAllPlayersInGame(long gameId, TableStateBroadcastResponse packet) {
        List<Connection> playerConnections = getConnectionsForGame(gameId);
        for (Connection connection : playerConnections) {
            connection.sendTCP(packet);
        }
    }

    public long createNewGame() {
        long gameId = generateUniqueGameId();
        ServerGameManager manager = new ServerGameManager();
        gameManagers.put(gameId, manager);
        connectionsPerGame.put(gameId, new ArrayList<>());
        startGameLoop(gameId, manager);
        return gameId;
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
    public void setUpServer(int tcpPort, int udpPort) throws IOException, ParserConfigurationException, SAXException {
        logger.info("Starting Kryo Server...");
        server.start(); // Start the server
        // Register all necessary packet classes for serialization
        PacketRegistrar.reloadConfigurationFromResource();
        PacketRegistrar.registerPackets(server.getKryo());
        logger.debug("\n" + PacketRegistrar.dumpRegisteredPackets());

        //LocalReg.registerPackets(server.getKryo());
        server.bind(tcpPort, udpPort); // Bind the server to the given ports

        // Add a listener to handle incoming requests
        server.addListener(new Listener.ThreadedListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                if (connection.getRemoteAddressTCP() == null) {
                    logger.debug("Received unknown object from {}: {}; Ignoring", connection.getRemoteAddressTCP(), object.getClass().getName());
                    return;
                }
                if (object instanceof FrameworkMessage) {
                    logger.debug("Received FrameworkMessage from client: {}", object.getClass().getName());
                    return;
                }
                if (object instanceof ClientHandshakeRequest request) {
                    logger.trace("received instance of {}...", ClientHandshakeRequest.class.getSimpleName());
                    handleClientHandshake(connection, (ClientHandshakeRequest) request);
                } else if (object instanceof DisconnectRequest request) {
                    logger.trace("received instance of {}...", DisconnectRequest.class.getSimpleName());
                    handleDisconnectRequest(connection,(DisconnectRequest) request);
                } else if (object instanceof TableStateUpdateRequest request) {
                    logger.trace("received instance of {}...", TableStateUpdateRequest.class.getSimpleName());
                    handleTableStateUpdateRequest(connection,(TableStateUpdateRequest) request);
                } else if (object instanceof MappedKeyUpdateRequest request) {
                    logger.trace("received instance of {}...", MappedKeyUpdateRequest.class.getSimpleName());
                    handlePlayerMappedKeyUpdateRequest(connection,(MappedKeyUpdateRequest) request);
                } else {
                    logger.warn("Received unexpected object: " + object.getClass().getName());
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
     * Gets a {@code DTOObject} from the persistence storage
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
    private void handleClientHandshake(Connection connection, ClientHandshakeRequest request) {
        YipeePlayer player = NetUtil.getPlayerFromNetYipeePlayer(request.getPlayer());
        String sessionId = UUID.randomUUID().toString() + TimeUtils.millis();

        PlayerConnectionDTO playerConnectDB = new PlayerConnectionDTO();
        playerConnectDB.setClientId(request.getClientId());
        playerConnectDB.setSessionId(sessionId);
        playerConnectDB.setConnected(true);
        playerConnectDB.setPlayer(player);
        playerConnectDB.setTimeStamp(TimeUtils.millis());
        playerConnectDB.setName(buildPlayerConnectionName(player));
        persistObject(playerConnectDB);

        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setSessionKey("session-xyz");
        response.setServerId(serverId);
        response.setTimestamp(System.currentTimeMillis());
        response.setPlayer(player);
        response.setConnected(true);

        logger.info("Handshake complete for clientId={}, player={}", request.getClientId(), player.getName());
        connection.sendTCP(response);
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

        YipeePlayer player = NetUtil.getPlayerFromNetYipeePlayer(request.getPlayer());
        String playerName = buildPlayerConnectionName(player);
        PlayerConnectionDTO user = null;
        try {
            user = getObjectByName(PlayerConnectionDTO.class, playerName);
            dePersistObject(user);
        } catch (Exception e) {
            logger.error("Failed to get Object:[{}] from database.", playerName);
        }

        // Send disconnection response
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setSessionKey("session-xyz");
        response.setServerId(serverId);
        response.setTimestamp(System.currentTimeMillis());
        response.setPlayer(player);
        response.setConnected(true);
    }

    private void handlePlayerMappedKeyUpdateRequest(Connection connection, MappedKeyUpdateRequest request) {
        PlayerConnectionDTO playerDTO = null;
        try {
            playerDTO = getObjectByName(PlayerConnectionDTO.class, request.getClientId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        YipeePlayer player = playerDTO.getPlayer();
        YipeeKeyMap newMap = request.getKeyConfig();
        player.setKeyConfig(newMap);
        logger.info("Updated key map for player {}", player.getName());

        // Send disconnection response
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setSessionKey("session-xyz");
        response.setServerId(serverId);
        response.setTimestamp(System.currentTimeMillis());
        response.setPlayer(player);
        response.setConnected(true);
    }

    private void handleTableStateUpdateRequest(Connection connection, TableStateUpdateRequest request) {
        // TODO: Fetch table by ID, validate player, apply partial update
        // Then broadcast new TableStateBroadcast
        logger.debug("Received TableStateUpdateRequest: {}", request);

        // Send disconnection response
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setSessionKey("session-xyz");
        response.setServerId(serverId);
        response.setTimestamp(System.currentTimeMillis());
        //response.setPlayer(player);
        response.setConnected(true);
    }

    private void startGameLoop(long clientId, ServerGameManager gameManager) {
        Runnable gameLoopTask = () -> gameManager.update(1 / 60f);
        gameExecutor.scheduleAtFixedRate(gameLoopTask, 0, 16, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the game server logic, such as broadcasting state or handling timeouts.
     *
     * @param deltaTime The time since the last update.
     */
    public void update(float deltaTime) {
        // increment on every tick
        incrementCurrentTick();

        for (Map.Entry<Long, ServerGameManager> entry : gameManagers.entrySet()) {
            ServerGameManager gameManager = entry.getValue();

            // 1. Run one tick of game logic
            gameManager.update(deltaTime);

            // 2. Get per-seat game state
            TableStateBroadcastResponse tickPacket = new TableStateBroadcastResponse();
            tickPacket.setServerTick(currentTick);
            tickPacket.setGameBoardStates(gameManager.getAllBoardStates());

            // 3. Send to only active players at this table
            for (Connection conn : gameManager.getActiveConnections()) {
                conn.sendTCP(tickPacket);
            }
        }
    }

    /**
     * Disposes of server resources gracefully.
     */
    @Override
    public void dispose() {
        try {
            logger.trace("Entering Game Dispose");
            server.dispose(); // Dispose of the server resources
            logger.info("GameServerManager shutting down ...");

            logger.info("Clearing threads...");
            // Stop all active game threads
            gameThreads.values().forEach(Thread::interrupt);
            gameThreads.clear();
            logger.info("threads cleared...");

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
            logger.info("GameServerManager shutdown complete ...");
            logger.trace("Exit Game Dispose");
        }
    }
}