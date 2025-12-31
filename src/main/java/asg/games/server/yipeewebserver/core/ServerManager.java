package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.server.yipeewebserver.net.listeners.YipeeKryoListener;
import asg.games.yipee.common.enums.YipeeObject;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.persistence.Storage;
import asg.games.yipee.core.tools.Util;
import asg.games.yipee.net.packets.TableStateUpdateResponse;
import asg.games.yipee.net.tools.PacketRegistrar;
import com.badlogic.gdx.utils.Disposable;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the game server, including networking, player connections, and game state updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerManager implements Disposable {
    private static final String ARG_USER_CONNECT_NAME_TAG = "#CONNECTION";
    private static final String ARG_NO_PLAYER_NAME_TAG = "_no_player_name";
    private static final String ARG_PACKETS_FILE = "libgdxPackets.xml";
    private final AtomicBoolean ticking = new AtomicBoolean(false);
    public static final String SERVER_STATUS_UP = "UP";
    public static final String SERVER_STATUS_DOWN = "DOWN";

    private final YipeePacketHandler yipeePacketHandler;
    private final GameContextFactory gameContextFactory;

    // The KryoNet server instance
    Server server = new Server();

    // Unique identifier for the server instance
    String serverId = UUID.randomUUID().toString();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Replace Kryo connections with WS subscribers.
    // MVP recommendation: key by tableId so watchers persist even if gameId changes later.
    private final Map<String, Set<WebSocketSession>> subscribersByTableId = new ConcurrentHashMap<>();

    // Maintain Kyro connections
    Map<String, List<Connection>> connectionsPerGame = new ConcurrentHashMap<>();

    private Storage storageAdapter;

    public void subscribeToTable(String tableId, WebSocketSession session) {
        subscribersByTableId
                .computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unsubscribeEverywhere(WebSocketSession session) {
        for (Set<WebSocketSession> set : subscribersByTableId.values()) {
            set.remove(session);
        }
    }

    public Set<WebSocketSession> getSubscribers(String tableId) {
        return subscribersByTableId.getOrDefault(tableId, Collections.emptySet());
    }

    /** Broadcasts one packet to all subscribers of a table. */
    public void broadcastToTable(String tableId, Object packet) {
        String json;
        try {
            json = objectMapper.writeValueAsString(packet);
        } catch (Exception e) {
            log.error("Failed to serialize packet for table {}", tableId, e);
            return;
        }

        TextMessage msg = new TextMessage(json);

        for (WebSocketSession s : Util.safeIterable(getSubscribers(tableId))) {
            try {
                if (s != null && s.isOpen()) {
                    s.sendMessage(msg);
                }
            } catch (Exception e) {
                log.warn("WS send failed; removing subscriber. tableId={}", tableId, e);
                unsubscribeEverywhere(s);
                try { s.close(); } catch (Exception ignore) {}
            }
        }
    }

    public void forceKickTable(String tableId, Object finalPacket) {
        // 1) Broadcast final message (optional)
        if (finalPacket != null) {
            broadcastToTable(tableId, finalPacket);
        }

        // 2) Close sessions + clear subscriptions
        Set<WebSocketSession> subs = subscribersByTableId.remove(tableId);
        if (subs != null) {
            for (WebSocketSession s : subs) {
                try { if (s != null && s.isOpen()) s.close(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Broadcasts the current game state to all connected clients.
     * This method should be called periodically during the game loop.
     */
    public void broadcastKryoServerResponses(List<TableStateUpdateResponse> responses) {
        for (TableStateUpdateResponse response : responses) {
            sendTCPs(getConnectionsFromGameId(response.getGameId()), response);
        }
    }

    private void sendTCPs(List<Connection> connections, TableStateUpdateResponse response) {
        for(Connection connection : Util.safeIterable(connections)) {
            if(connection != null && connection.isConnected()) {
                connection.sendTCP(response);
            }
        }
    }

    public List<Connection> getConnectionsFromGameId(String gameId) {
        return connectionsPerGame.getOrDefault(gameId, Collections.emptyList());
    }

    public String createNewGame() {
        String gameId = gameContextFactory.newGame();
        connectionsPerGame.put(gameId, new ArrayList<>());
        return gameId;
    }

    public ServerGameManager getGame(String gameId) {
        return gameContextFactory.getGame(gameId);
    }

    /**
     * Initializes the Database Persistence Object
     */
    public void setDBService(Storage yipeeGameServices) {
        storageAdapter = yipeeGameServices;
    }

    /**
     * Sets up and starts the Kryo server, binding to the specified TCP and UDP ports.
     *
     * @param tcpPort The port for TCP connections.
     * @param udpPort The port for UDP connections.
     * @throws IOException if there is an error during server binding.
     */
    public void setUpKryoServer(int tcpPort, int udpPort) throws IOException, ParserConfigurationException, SAXException {
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
        server.addListener(new Listener.ThreadedListener(new YipeeKryoListener(yipeePacketHandler, gameContextFactory)));
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
        List<TableStateUpdateResponse> serverResponses = new LinkedList<>();

        for (ServerGameManager gameManager : gameContextFactory.getAllGames()) {
            // 1. Run one tick of THIS game's logic
            try {
                gameManager.update(deltaTime);   // serverTick++ happens inside
            } catch (JsonProcessingException e) {
                log.error("Error updating game {}", gameManager.getGameId(), e);
            }

            // 2. Build a per-game tick packet
            long tick = gameManager.getServerTick();
            TableStateUpdateResponse tickPacket = new TableStateUpdateResponse();
            tickPacket.setServerTick(tick);  // per-game tick
            tickPacket.setGameId(gameManager.getGameId());
            tickPacket.setServerId(serverId);
            //serverResponses.add(tickPacket);

            // MVP requirement: we must know which table to broadcast to
            broadcastToTable(gameManager.getTableId(), tickPacket);

            if (gameManager.checkGameEndConditions()) {
                gameManager.endGameLoop();

                // MVP: tell clients to close
                var end = new java.util.HashMap<String, Object>();
                end.put("type", "GAME_END");
                end.put("tableId", gameManager.getTableId());
                end.put("gameId", gameManager.getGameId());
                end.put("serverTick", gameManager.getServerTick());

                //TODO: Send game end Response so that end message can be annimated.
                // optional: send game-over packet then kick
                //forceKickTable(gm.getTableId(), /* GameEndResponse */ null);
            }
        }

        // 3. Send to only active players at this table
        //broadcastKryoServerResponses(serverResponses);
    }

    /**
     * Disposes of server resources gracefully.
     */
    @Override
    public void dispose() {
        try {
            log.trace("Entering Game Dispose");
            //connectionsPerGame.clear();

            // Stop Kryo servers
            if (server != null) {
                server.stop();
            }

            for (Set<WebSocketSession> set : subscribersByTableId.values()) {
                for (WebSocketSession s : set) {
                    try { if (s != null && s.isOpen()) s.close(); } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            log.error("Error while shutting down GameServerManager", e);
            throw new RuntimeException(e);
        } finally {
            log.trace("Exit Game Dispose");
        }
    }
}