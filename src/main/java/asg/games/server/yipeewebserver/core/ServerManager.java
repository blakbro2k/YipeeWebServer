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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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

    Map<String, List<Connection>> connectionsPerGame = new ConcurrentHashMap<>();

    private Storage storageAdapter;

    /**
     * Broadcasts the current game state to all connected clients.
     * This method should be called periodically during the game loop.
     */
    public void broadcastServerResponses(List<TableStateUpdateResponse> responses) {
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
            TableStateUpdateResponse tickPacket = new TableStateUpdateResponse();
            tickPacket.setServerTick(gameManager.getServerTick());  // per-game tick
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
            connectionsPerGame.clear();
        } catch (Exception e) {
            log.error("Error while shutting down GameServerManager", e);
            throw new RuntimeException(e);
        } finally {
            log.trace("Exit Game Dispose");
        }
    }
}