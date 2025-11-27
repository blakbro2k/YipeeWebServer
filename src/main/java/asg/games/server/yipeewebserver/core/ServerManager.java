package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.net.ConnectionContext;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.yipee.common.enums.YipeeObject;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.persistence.Storage;
import asg.games.yipee.core.tools.Util;
import asg.games.yipee.net.errors.YipeeBadRequestException;
import asg.games.yipee.net.errors.YipeeException;
import asg.games.yipee.net.packets.AbstractClientRequest;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.DisconnectRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
import asg.games.yipee.net.packets.TableStateBroadcastResponse;
import asg.games.yipee.net.packets.TableStateUpdateRequest;
import asg.games.yipee.net.tools.PacketRegistrar;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Manages the game server, including networking, player connections, and game state updates.
 */
@Slf4j
public class ServerManager implements Disposable {
    private static final String ARG_USER_CONNECT_NAME_TAG = "#CONNECTION";
    private static final String ARG_NO_PLAYER_NAME_TAG = "_no_player_name";
    private static final String ARG_PACKETS_FILE = "libgdxPackets.xml";
    private final AtomicBoolean ticking = new AtomicBoolean(false);
    public static final String SERVER_STATUS_UP = "UP";
    public static final String SERVER_STATUS_DOWN = "DOWN";

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
    private final Map<Integer, ConnectionContext> connectionContexts = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToGameId = new ConcurrentHashMap<>();

    private Storage storageAdapter;

    private YipeePacketHandler yipeePacketHandler;

    /**
     * Constructor for GameServerManager.
     */
    public ServerManager() {}

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
     * Initializes the Database Persistence Object
     */
    public void setDBService(Storage yipeeGameServices) {
        storageAdapter = yipeeGameServices;
    }

    /**
     * Initializes the Network Package Handler Object
     */
    public void setNetworkHandlerService(YipeePacketHandler yipeePacketHandler) {
        this.yipeePacketHandler = yipeePacketHandler;
    }

    /**
     * Sets up and starts the game server, binding to the specified TCP and UDP ports.
     *
     * @param tcpPort The port for TCP connections.
     * @param udpPort The port for UDP connections.
     * @throws IOException if there is an error during server binding.
     */
    public void setUpServer(int tcpPort, int udpPort) throws IOException, ParserConfigurationException, SAXException {
        log.info("Starting Kryo Server...");
        server.start(); // Start the server

        File file = ResourceUtils.getFile("src/main/resources/packets.xml");
        if(!file.exists()) {
            throw new FileNotFoundException("Could not find a valid packet.xml file.");
        }

        // Register all necessary packet classes for serialization
        PacketRegistrar.reloadConfiguration(file.getPath());
        PacketRegistrar.registerPackets(server.getKryo());
        log.debug("\n" + PacketRegistrar.dumpRegisteredPackets());

        //LocalReg.registerPackets(server.getKryo());
        server.bind(tcpPort, udpPort); // Bind the server to the given ports

        // Add a listener to handle incoming requests
        server.addListener(new Listener.ThreadedListener(new Listener() {
            @Override
            public void received(Connection connection, Object object) {
                try {
                    updateConnectionContext(connection, object);

                    if (connection.getRemoteAddressTCP() == null) {
                        log.debug("Received unknown object from {}: {}; Ignoring", connection.getRemoteAddressTCP(), object.getClass().getName());
                        return;
                    }
                    if (object instanceof FrameworkMessage) {
                        log.trace("Received FrameworkMessage from client: {}", object.getClass().getName());
                        return;
                    }
                    if (object instanceof ClientHandshakeRequest request) {
                        log.trace("received instance of {}...", ClientHandshakeRequest.class.getSimpleName());
                        yipeePacketHandler.handleClientHandshake(connection, request);
                        return;
                    }
                    if (object instanceof DisconnectRequest request) {
                        log.trace("received instance of {}...", DisconnectRequest.class.getSimpleName());
                        yipeePacketHandler.handleDisconnectRequest(connection, request);
                        return;
                    }
                    if (object instanceof TableStateUpdateRequest request) {
                        log.trace("received instance of {}...", TableStateUpdateRequest.class.getSimpleName());
                        yipeePacketHandler.handleTableStateUpdateRequest(connection, request);
                        return;
                    }
                    if (object instanceof MappedKeyUpdateRequest request) {
                        log.trace("received instance of {}...", MappedKeyUpdateRequest.class.getSimpleName());
                        yipeePacketHandler.handlePlayerMappedKeyUpdateRequest(connection, request);
                        return;
                    }
                    throw new YipeeBadRequestException("Received unexpected object: " + object.getClass().getName());

                } catch (YipeeException ye) {
                    // Custom expected game/network error
                    log.warn("Yipee error: {}", ye.getMessage());
                    yipeePacketHandler.handleNetError(connection, ye);
                } catch (Exception e) {
                    // Unexpected internal crash
                    log.error("Unhandled server error", e);
                    yipeePacketHandler.handleNetError(connection, e);
                }

            }
        }));
    }

    private void updateConnectionContext(Connection connection, Object object) {
        if (!(object instanceof AbstractClientRequest req)) return;

        ConnectionContext connectionContext = connectionContexts.computeIfAbsent(
                connection.getID(),
                id -> new ConnectionContext()
        );

        connectionContext.clientId = req.getClientId();
        connectionContext.sessionId = req.getSessionId();
        // gameId will be resolved by server after lookup:
        connectionContext.gameId = findGameIdForSession(req.getSessionId());
    }

    private String findGameIdForSession(String sessionId) {
        if (sessionId == null) return null;
        return sessionToGameId.get(sessionId);
    }


    /**
     * Saves a {@code YipeeObject} to the persistence storage
     *
     * @param object object to save to database
     */
    private void persistObject(YipeeObject object) {
        if(storageAdapter != null) {
            storageAdapter.saveObject(object);
        }
    }

    /**
     * Deletes a {@code YipeeObject} from the persistence storage
     *
     * @param object object to delete from database
     */
    private void dePersistObject(YipeeObject object) {
        if(storageAdapter != null) {
            storageAdapter.deleteObject(object);
        }
    }

    /**
     * Gets a {@code YipeeObject} from the persistence storage
     *
     * @param object to get from database
     */
    private <T extends YipeeObject> T getPersistObject(Class<T> clazz, YipeeObject object) {
        T obj = null;
        if(storageAdapter != null) {
            try {
                obj = getObjectByName(clazz, object.getName());
                if(obj == null) {
                    obj = getObjectById(clazz, object.getId());
                }
            } catch (Exception e) {
                log.error("Failed to getPersistObject Object:[{}] from database.", obj);
            }
        }
        return obj;
    }

    private <T extends YipeeObject> T getObjectByName(Class<T> clazz, String name) throws Exception {
        return storageAdapter.getObjectByName(clazz, name);
    }

    private <T extends YipeeObject> T getObjectById(Class<T> clazz, String id) throws Exception {
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
            log.trace("Entering Game Dispose");

            if (server != null) server.stop();

            gameManagers.clear();
            connectionsPerGame.clear();
            connectionToGameIdx.clear();

        } catch (Exception e) {
            log.error("Error while shutting down GameServerManager", e);
            throw new RuntimeException(e);
        } finally {
            log.trace("Exit Game Dispose");
        }
    }

}