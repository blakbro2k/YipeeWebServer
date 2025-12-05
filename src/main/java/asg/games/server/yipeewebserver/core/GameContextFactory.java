package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.net.ConnectionContext;
import asg.games.server.yipeewebserver.net.ConnectionContextFactory;
import asg.games.yipee.net.packets.AbstractClientRequest;
import com.esotericsoftware.kryonet.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.InvalidObjectException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory responsible for building {@link GameContext} objects for all transports
 * (Kryo and WebSocket) and managing the lifecycle of {@link ServerGameManager} instances.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Derive gameId / playerId from a {@link ConnectionContext} or incoming request</li>
 *   <li>Look up or create the appropriate {@link ServerGameManager} for that game</li>
 *   <li>Produce a transport-agnostic {@link GameContext} snapshot for packet handling</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GameContextFactory {
    private static final int CONST_UPPER_ID_LIMIT = 8;
    private static final int CONST_LOWER_ID_LIMIT = 0;

    private final ServerIdentity serverIdentity;
    private final ConnectionContextFactory connectionContextFactory;
    private final ConcurrentHashMap<String, ServerGameManager> gameManagers = new ConcurrentHashMap<>();

    /**
     * Builds a {@link GameContext} for a KryoNet connection, updating the
     * {@link ConnectionContext} for that connection first.
     *
     * @param connection active KryoNet connection
     * @param req        the incoming client request
     * @return a populated {@link GameContext} for this request
     */
    public GameContext fromKryo(Connection connection, AbstractClientRequest req) throws InvalidObjectException {
        ConnectionContext ctx = connectionContextFactory.updateConnectionContext(connection, req);
        return buildFrom(ctx, req);
    }

    /**
     * Builds a {@link GameContext} for a WebSocket session. Expects an optional
     * {@link ConnectionContext} to be stored under the "connectionContext" attribute
     * on the {@link WebSocketSession}. If none is found, the resulting context may
     * have a {@code null} gameId and a {@code serverTick} of 0.
     *
     * @param session WebSocket session
     * @param req     the incoming client request
     * @return a populated {@link GameContext} for this request
     */
    public GameContext fromWebSocket(WebSocketSession session, AbstractClientRequest req) {
        ConnectionContext ctx = (ConnectionContext) session.getAttributes().get("connectionContext");
        return buildFrom(ctx, req);
    }

    private GameContext buildFrom(ConnectionContext ctx, AbstractClientRequest req) {
        String gameId   = (ctx != null && ctx.getGameId()   != null) ? ctx.getGameId()   : req.getGameId();
        String playerId = (ctx != null && ctx.getPlayerId() != null) ? ctx.getPlayerId() : null;

        ServerGameManager game = getGame(gameId);
        long now       = System.currentTimeMillis();
        long serverTick = (game != null) ? game.getServerTick() : 0L;

        return new GameContext(
                serverIdentity.getServiceName(),
                serverIdentity.getFullId(),
                serverTick,
                req.getClientId(),
                gameId,
                playerId,
                now
        );
    }

    /**
     * Build a GameContext using IDs only, so APIs / WebSockets / Kryo
     * can all share this path.
     */
    public GameContext fromIds(String gameId, String playerId, String clientId, long serverTick) {
        long now       = System.currentTimeMillis();

        return new GameContext(
                serverIdentity.getServiceName(),
                serverIdentity.getFullId(),
                serverTick,
                clientId,
                gameId,
                playerId,
                now
        );
    }

    private String generateUniqueGameId() {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(CONST_LOWER_ID_LIMIT, CONST_UPPER_ID_LIMIT);
        }
        while (gameManagers.containsKey(id));
        return id;
    }

    /**
     * Creates and registers a new {@link ServerGameManager} with a unique game id.
     *
     * @return the generated game id for the new game
     */
    public String newGame() {
        ServerGameManager manager = new ServerGameManager(ServerGameManager.MAX_TICK_HISTORY);
        String gameId = generateUniqueGameId();
        manager.setGameId(gameId);
        gameManagers.put(gameId, manager);
        return gameId;
    }

    /**
     * Returns the {@link ServerGameManager} for the given game id, or {@code null}
     * if no such game is currently registered.
     */
    public ServerGameManager getGame(String gameId) {
        return gameManagers.get(gameId);
    }

    /**
     * Returns a view of all currently registered {@link ServerGameManager} instances.
     * <p>
     * The returned collection is backed by the underlying map and will reflect
     * concurrent additions/removals.
     */
    public Collection<ServerGameManager> getAllGames() {
        return gameManagers.values();
    }
}