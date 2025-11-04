package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.data.DTOObject;
import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.yipee.net.tools.PacketRegistrar;
import asg.games.yipee.core.objects.YipeeKeyMap;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.persistence.Storage;
import asg.games.yipee.core.tools.Util;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ResourceUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static asg.games.server.yipeewebserver.tools.NetUtil.getPlayerFromNetYipeePlayer;

/**
 * Manages the game server, including networking, player connections, and game state updates.
 */
public class ServerManager implements Disposable {
    private static final Logger logger = LoggerFactory.getLogger(ServerManager.class);
    private static final String ARG_USER_CONNECT_NAME_TAG = "#CONNECTION";
    private static final String ARG_NO_PLAYER_NAME_TAG = "_no_player_name";
    private static final String ARG_PACKETS_FILE = "libgdxPackets.xml";
    private final AtomicBoolean ticking = new AtomicBoolean(false);

    // The KryoNet server instance
    Server server = new Server();

    // Unique identifier for the server instance
    String serverId = UUID.randomUUID().toString();
    // Note: serverTick uses int because matches reset frequently.
    // At 60 tps it overflows after ~1 year; safe for per-session lifespan.
    private int serverTick = 0;
    private final ConcurrentHashMap<String, Thread> gameThreads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServerGameManager> gameManagers = new ConcurrentHashMap<>();
    Map<String, List<Connection>> connectionsPerGame = new ConcurrentHashMap<>();
    private final Map<Integer, String> connectionToGameIdx = new ConcurrentHashMap<>();

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
    public void broadcastServerResponses(List<TableStateBroadcastResponse> responses) {
        for (TableStateBroadcastResponse response : responses) {
            sendTCPs(getConnectionsFromGameId(response.getGameId()), response);
        }
    }

    private void sendTCPs(List<Connection> connections, TableStateBroadcastResponse response) {
        for(Connection connection : Util.safeIterable(connections)) {
            if(connection != null && connection.isConnected()) {
                connection.sendTCP(response);
            }
        }
    }

    public List<Connection> getConnectionsFromGameId(String gameId) {
        return connectionsPerGame.getOrDefault(gameId, Collections.emptyList());
    }

    private int getServerTick() {
        return serverTick;
    }

    private void incrementServerTick() {
        serverTick++;
    }

    public String createNewGame() {
        ServerGameManager manager = new ServerGameManager();
        String gameId = generateUniqueGameId();
        manager.setGameId(gameId);
        gameManagers.put(gameId, manager);
        connectionsPerGame.put(gameId, new ArrayList<>());
        return gameId;
    }

    private String generateUniqueGameId() {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        while (gameManagers.containsKey(id));
        return id;
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

        File file = ResourceUtils.getFile("src/main/resources/packets.xml");
        if(!file.exists()) {
            throw new FileNotFoundException("Could not find a valid packet.xml file.");
        }

        // Register all necessary packet classes for serialization
        PacketRegistrar.reloadConfiguration(file.getPath());
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
                    logger.trace("Received FrameworkMessage from client: {}", object.getClass().getName());
                    return;
                }
                if (object instanceof ClientHandshakeRequest request) {
                    logger.trace("received instance of {}...", ClientHandshakeRequest.class.getSimpleName());
                    handleClientHandshake(connection, request);
                } else if (object instanceof DisconnectRequest request) {
                    logger.trace("received instance of {}...", DisconnectRequest.class.getSimpleName());
                    handleDisconnectRequest(connection, request);
                } else if (object instanceof TableStateUpdateRequest request) {
                    logger.trace("received instance of {}...", TableStateUpdateRequest.class.getSimpleName());
                    handleTableStateUpdateRequest(connection, request);
                } else if (object instanceof MappedKeyUpdateRequest request) {
                    logger.trace("received instance of {}...", MappedKeyUpdateRequest.class.getSimpleName());
                    handlePlayerMappedKeyUpdateRequest(connection, request);
                } else {
                    logger.warn("Received unexpected object: " + object.getClass().getName());
                }
            }
        }));
    }

    /**
     * Saves a {@code DTOObject} to the persistence storage
     *
     * @param object object to save to database
     */
    private void persistObject(DTOObject object) {
        if(storageAdapter != null) {
            storageAdapter.saveObject(object);
        }
    }

    /**
     * Deletes a {@code DTOObject} from the persistence storage
     *
     * @param object object to delete from database
     */
    private void dePersistObject(DTOObject object) {
        if(storageAdapter != null) {
            storageAdapter.deleteObject(object);
        }
    }

    /**
     * Gets a {@code DTOObject} from the persistence storage
     *
     * @param object to get from database
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
        logger.trace("Enter handleClientHandshake({}, {})", connection, request);

        //TODO: valdiate non duplicate clientID
        //TODO: generate unique session id (clientid+server nonce)
        //TODO: generate authToken

        YipeePlayer player = getPlayerFromNetYipeePlayer(request.getPlayer());
        String sessionId = UUID.randomUUID().toString() + TimeUtils.millis();

        PlayerConnectionDTO playerConnectDTO = new PlayerConnectionDTO();
        playerConnectDTO.setClientId(request.getClientId());
        playerConnectDTO.setSessionId(sessionId);
        playerConnectDTO.setConnected(true);
        playerConnectDTO.setPlayer(player);
        playerConnectDTO.setTimeStamp(TimeUtils.millis());
        playerConnectDTO.setName(buildPlayerConnectionName(player));

        logger.debug("Saving [{}] object.", playerConnectDTO);
        persistObject(playerConnectDTO);

        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setAuthToken("");
        response.setServerId(serverId);
        response.setServerTimestamp(System.currentTimeMillis());
        response.setPlayer(player);
        response.setConnected(true);

        logger.info("Handshake complete for clientId={}, player={}", request.getClientId(), player.getName());
        connection.sendTCP(response);
        logger.trace("Exit handleClientHandshake()");
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

        YipeePlayer player = getPlayerFromNetYipeePlayer(request.getPlayer());
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
        response.setAuthToken("session-xyz");
        response.setServerId(serverId);
        response.setServerTimestamp(System.currentTimeMillis());
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
        YipeeKeyMap newMap = getPlayerFromNetYipeePlayer(request.getKeyConfig());
        player.setKeyConfig(newMap);
        logger.info("Updated key map for player {}", player.getName());

        // Send disconnection response
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setAuthToken("session-xyz");
        response.setServerId(serverId);
        response.setServerTimestamp(System.currentTimeMillis());
        response.setPlayer(player);
        response.setConnected(true);
    }

    private void handleTableStateUpdateRequest(Connection connection, TableStateUpdateRequest request) {
        // TODO: Fetch table by ID, validate player, apply partial update
        // Then broadcast new TableStateBroadcast
        logger.debug("Received TableStateUpdateRequest: {}", request);

        // Send disconnection response
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setAuthToken("session-xyz");
        response.setServerId(serverId);
        response.setServerTimestamp(System.currentTimeMillis());
        //response.setPlayer(player);
        response.setConnected(true);
    }

    /**
     * Updates the game server logic, such as broadcasting state or handling timeouts.
     *
     * @param deltaTime The time since the last update.
     */
    public void update(float deltaTime) {
        // increment on every tick
        incrementServerTick();

        List<TableStateBroadcastResponse> serverResponses = new LinkedList<>();
        for (Map.Entry<String, ServerGameManager> entry : gameManagers.entrySet()) {
            ServerGameManager gameManager = entry.getValue();

            // 1. Run one tick of game logic
            try {
                gameManager.update(deltaTime, getServerTick());
            } catch (JsonProcessingException e) {
                //throw new RuntimeException(e);
            }

            // 2. Get per-seat game state
            TableStateBroadcastResponse tickPacket = new TableStateBroadcastResponse();
            tickPacket.setServerTick(getServerTick());
            //tickPacket.setStates(gameManager.getAllBoardStates());
            tickPacket.setGameId(gameManager.getGameId());
            tickPacket.setServerId(serverId);
            serverResponses.add(tickPacket);
        }

        // 3. Send to only active players at this table
        broadcastServerResponses(serverResponses);
    }

    /**
     * Disposes of server resources gracefully.
     */
    @Override
    public void dispose() {
        try {
            logger.trace("Entering Game Dispose");

            if (server != null) server.stop();

            gameManagers.clear();
            connectionsPerGame.clear();
            connectionToGameIdx.clear();

        } catch (Exception e) {
            logger.error("Error while shutting down GameServerManager", e);
            throw new RuntimeException(e);
        } finally {
            logger.trace("Exit Game Dispose");
        }
    }
}