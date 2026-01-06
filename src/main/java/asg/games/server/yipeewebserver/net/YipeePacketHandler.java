package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.core.GameContext;
import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.core.ServerGameManager;
import asg.games.server.yipeewebserver.services.GameSessionService;
import asg.games.server.yipeewebserver.tools.Util;
import asg.games.yipee.net.errors.ErrorCode;
import asg.games.yipee.net.errors.ErrorMapper;
import asg.games.yipee.net.errors.YipeeException;
import asg.games.yipee.net.packets.AbstractClientRequest;
import asg.games.yipee.net.packets.AbstractServerResponse;
import asg.games.yipee.net.packets.ErrorResponse;
import asg.games.yipee.net.packets.GameStartRequest;
import asg.games.yipee.net.packets.GameStartResponse;
import asg.games.yipee.net.packets.GameSubscribeRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateResponse;
import asg.games.yipee.net.packets.PlayerActionRequest;
import asg.games.yipee.net.packets.PlayerActionResponse;
import asg.games.yipee.net.packets.SeatStateUpdateRequest;
import asg.games.yipee.net.packets.SeatStateUpdateResponse;
import asg.games.yipee.net.packets.TableActionsBroadcastResponse;
import asg.games.yipee.net.packets.TableStateUpdateRequest;
import asg.games.yipee.net.packets.TableStateUpdateResponse;
import com.esotericsoftware.kryonet.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * YipeePacketHandler is now responsible ONLY for in-game messages:
 * <p>
 * - GameStartRequest / GameStartResponse
 * - PlayerActionRequest / PlayerActionResponse
 * - MappedKeyUpdateRequest / MappedKeyUpdateResponse
 * - TableStateUpdateRequest
 * - Broadcast packets (TableStateBroadcastResponse, TableActionsBroadcastResponse, AllStatesBroadcastResponse)
 * - ErrorResponse (via ErrorMapper)
 * <p>
 * It NO LONGER:
 * <p>
 * - performs handshake / session creation
 * - touches JPA repositories
 * - manages lobby (rooms / tables / players) – that's all HTTP + JPA now.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YipeePacketHandler {
    private final ServerIdentity serverIdentity;
    private final ConnectionContextFactory connectionContextFactory;
    private final GameContextFactory gameContextFactory;
    private final GameSessionService gameSessionService;

    public static final String IDENTITY_PROVIDER_WORDPRESS = "WORDPRESS";

    // ========================================================================
    //  Core transport-agnostic handler
    // ========================================================================

    /**
     * Central dispatcher for all in-game client requests.
     * Called by whichever transport (Kryo, WebSocket, etc.) is receiving packets.
     */
    public AbstractServerResponse handle(GameContext gameContext, AbstractClientRequest request) {
        try {
            if (request instanceof GameSubscribeRequest r) {
                return handleGameSubscription(gameContext, r);
            } else if (request instanceof GameStartRequest r) {
                return handleGameStart(gameContext, r);
            } else if (request instanceof MappedKeyUpdateRequest r) {
                return handleMappedKeyUpdate(gameContext, r);
            } else if (request instanceof PlayerActionRequest r) {
                return handlePlayerAction(gameContext, r);
            } else if (request instanceof TableStateUpdateRequest r) {
                return handleTableStateUpdate(gameContext, r);
            } else if (request instanceof SeatStateUpdateRequest r) {
                return handleSeatStateUpdate(gameContext, r);
            } else {
                log.warn("Unsupported in-game packet type: {}", request.getClass().getName());
                return errorResponse(request, ErrorCode.UNSUPPORTED_OPERATION, "Unsupported in-game packet type");
            }
        } catch (YipeeException ex) {
            log.warn("YipeeException while handling {}: {}", request, ex.getMessage());
            return errorResponse(request, ErrorMapper.toCode(ex), ex.getMessage());
        } catch (Exception ex) {
            log.error("Unhandled exception while handling {}: {}", request, ex.getMessage(), ex);
            return errorResponse(request, ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    /**
     * Attempts to safely resolve an {@link AbstractServerResponse} instance into a concrete
     * response type by performing a runtime type check against the provided {@code Class<T>}.
     * <p>
     * This method is useful when the caller expects a specific subclass of
     * {@link AbstractServerResponse} but the actual response is obtained from a generic
     * or transport-agnostic source (e.g., Kryo, WebSocket, or REST handler) where the
     * concrete type is not known at compile time.
     * <p>
     * The method performs a type-safe check using {@link Class#isInstance(Object)} and
     * returns {@code null} when the provided response does not match the expected type.
     * No {@link ClassCastException} will be thrown.
     *
     * <h2>Example:</h2>
     *
     * <pre>{@code
     * AbstractServerResponse resp = packetHandler.handle(ctx, request);
     *
     * GameStartResponse start =
     *         resolve(GameStartResponse.class, resp);
     *
     * if (start != null) {
     *     // Handle game start response
     * }
     * }</pre>
     *
     * @param <T>      the expected concrete response type
     * @param type     the {@link Class} object representing the expected response type
     * @param response the response instance to check and cast
     * @return the response cast to type {@code T} if compatible; otherwise {@code null}
     */
    public static <T extends AbstractServerResponse> T getClassResponse(Class<T> type, AbstractServerResponse response) {
        return type.isInstance(response) ? type.cast(response) : null;
    }

    // ========================================================================
    //  Specific packet handlers
    // ========================================================================
    private AbstractServerResponse handleGameSubscription(GameContext gameContext, GameSubscribeRequest req) {
        log.debug("Handling GameSubscribeRequest: {}", req);

        String gameId = gameContext.gameId();
        if (gameId == null || gameId.isBlank()) {
            return errorResponse(req, ErrorCode.BAD_REQUEST, "gameId missing from context");
        }

        ServerGameManager gameManager = gameContextFactory.getGame(gameId);
        if (gameManager == null) {
            return errorResponse(req, ErrorCode.BAD_REQUEST, gameId + " gameId does not exist.");
        }

        // MVP placeholder
        return errorResponse(req, ErrorCode.UNSUPPORTED_OPERATION, "GameSubscribe not implemented yet");
    }


    private GameStartResponse handleGameStart(GameContext gameContext, GameStartRequest req) {
        log.debug("Handling GameStartRequest: {}", req);

        String tableId = req.getTableId();
        if (tableId == null || tableId.isBlank()) {
            throw new IllegalArgumentException("tableId required");
        }

        String gameId = gameContext.gameId();
        if (gameId == null || gameId.isBlank()) throw new IllegalArgumentException("gameId required");

        ServerGameManager gameManager = gameContextFactory.getGame(gameId);
        if (gameManager == null) {
            throw new IllegalArgumentException(gameId + " gameId does not exist.");
        }

        // Bind session -> game for future requests/broadcast routing
        gameSessionService.bindGame(req.getSessionId(), req.getClientId(), gameId);

        GameStartResponse resp = new GameStartResponse();
        Util.copyEnvelope(req, resp);
        resp.setAccepted(true);
        resp.setGameId(gameId);
        resp.setTableId(tableId);

        Util.stampServerMeta(resp, serverIdentity);
        return resp;
    }


    private MappedKeyUpdateResponse handleMappedKeyUpdate(GameContext gameContext, MappedKeyUpdateRequest req) {
        log.debug("Handling MappedKeyUpdateRequest: {}", req);

        // TODO: apply to your player input configuration:
        // mappedKeyService.updateKeyMap(req.getPlayerId(), req.getKeyConfig());

        MappedKeyUpdateResponse resp = new MappedKeyUpdateResponse();
        Util.copyEnvelope(req, resp);
        resp.setSuccess(true);

        Util.stampServerMeta(resp, serverIdentity);
        return resp;
    }

    private PlayerActionResponse handlePlayerAction(GameContext gameContext, PlayerActionRequest req) {
        log.debug("Enter handlePlayerAction(gameContext={}, req={})", gameContext, req);

        PlayerActionResponse resp = new PlayerActionResponse();
        Util.copyEnvelope(req, resp);
        resp.setAccepted(true);

        // For now, just echo the client tick; later you can set the authoritative tick.
        resp.setServerTick(req.getClientTick());

        Util.stampServerMeta(resp, serverIdentity);
        log.debug("Exit handlePlayerAction()={}", resp);
        return resp;
    }

    /**
     * TableStateUpdateRequest might be admin/host-only. Typically you either:
     * - apply a state patch and then broadcast, or
     * - reject it as invalid.
     * <p>
     * For now, we accept and echo via a TableStateBroadcastResponse.
     */
    private AbstractServerResponse handleTableStateUpdate(GameContext gameContext, TableStateUpdateRequest req) {
        log.debug("Handling TableStateUpdateRequest: {}", req);

        // TODO: apply patch into your GameManager and then broadcast.
        // TODO: Get table information by getTableId()
        // TODO: Record user requesting update
        // TODO: generate SeatStateUpdate request from list of gamestates for each seat
        TableStateUpdateResponse tableUpdateRes = new TableStateUpdateResponse();
        Util.copyEnvelope(req, tableUpdateRes);
        tableUpdateRes.setGameId(gameContext.gameId());
        tableUpdateRes.setSeatState1(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState2(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState3(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState4(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState5(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState6(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState7(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));
        tableUpdateRes.setSeatState8(buildSeatStateUpdateResponse(gameContext.gameId(), gameContext.serverTick(), gameContext, null));

        Util.stampServerMeta(tableUpdateRes, serverIdentity);
        return tableUpdateRes;
    }

    /**
     * TableStateUpdateRequest might be admin/host-only. Typically you either:
     * - apply a state patch and then broadcast, or
     * - reject it as invalid.
     * <p>
     * For now, we accept and echo via a TableStateBroadcastResponse.
     */
    private AbstractServerResponse handleSeatStateUpdate(GameContext gameContext, SeatStateUpdateRequest req) {
        log.debug("Handling TableStateUpdateRequest: {}", req);

        // TODO: apply patch into your GameManager and then broadcast.
        SeatStateUpdateResponse broadcast = new SeatStateUpdateResponse();
        Util.copyEnvelope(req, broadcast);
        broadcast.setGameId(req.getGameId());
        broadcast.setTableId(req.getTableId());
        broadcast.setStates(new ArrayList<>());

        Util.stampServerMeta(broadcast, serverIdentity);
        return broadcast;
    }

    // ========================================================================
    //  Broadcast builders (for GameManager to call)
    // ========================================================================

    public TableActionsBroadcastResponse buildActionsBroadcast(
            String gameId,
            String tableId,
            int serverTick,
            Object actionsPayload
    ) {
        TableActionsBroadcastResponse resp = new TableActionsBroadcastResponse();
        resp.setGameId(gameId);
        resp.setTableId(tableId);
        resp.setActions(null);
        resp.setServerTick(serverTick);
        Util.stampServerMeta(resp, serverIdentity);
        return resp;
    }

    public SeatStateUpdateResponse buildSeatStateUpdateResponse(
            String gameId,
            long serverTick,
            GameContext gameContext,
            Object allStatesPayload
    ) {
        SeatStateUpdateResponse resp = new SeatStateUpdateResponse();
        resp.setGameId(gameId);
        resp.setStates(new ArrayList<>());
        resp.setServerTick(gameContext.serverTick());
        Util.stampServerMeta(resp, serverIdentity);
        return resp;
    }

    // ========================================================================
    //  Error handling
    // ========================================================================

    private ErrorResponse errorResponse(AbstractClientRequest req,
                                        ErrorCode code,
                                        String message) {
        ErrorResponse err = new ErrorResponse();
        Util.copyEnvelope(req, err);
        err.setCode(code);
        err.setMessage(message);
        err.setDetails(req.getClass().getSimpleName());
        Util.stampServerMeta(err, serverIdentity);
        return err;
    }

    /**
     * For errors thrown somewhere else in the Kryo pipeline, where we only have
     * a Connection and a Throwable.
     */
    public ErrorResponse processNetError(Connection connection, Throwable t) {
        ErrorResponse err = new ErrorResponse();
        err.setServerId(serverIdentity.getFullId());
        err.setServerTimestamp(System.currentTimeMillis());

        ConnectionContext ctx = connectionContextFactory.getConnectionContextByConnectionId(connection.getID());

        if (ctx != null) {
            if (ctx.getSessionId() != null) {
                err.setSessionId(ctx.getSessionId());
            }
            if (ctx.getGameId() != null) {
                err.setGameId(ctx.getGameId());
            }
        }

        err.setCode(ErrorMapper.toCode(t));
        err.setMessage(t.getMessage());
        err.setDetails(t.getClass().getSimpleName());

        if (!(t instanceof YipeeException)) {
            log.error("Unhandled internal exception", t);
        } else {
            log.warn("Yipee error: {}", t.getMessage());
        }
        return err;
    }

    public void handleNetError(Connection connection, Throwable t) {
        ErrorResponse err = processNetError(connection, t);
        if (connection.isConnected()) {
            connection.sendTCP(err);
        }
    }

    // ========================================================================
    //  KryoNet wrapper
    // ========================================================================

    /**
     * Single entrypoint from your KryoNet Listener.
     * Example usage in your Listener:
     * <p>
     * public void received(Connection c, Object o) {
     *  if (o instanceof AbstractClientRequest req) {
     *  packetHandler.handleKryoRequest(c, req);
     *  }
     * }
     */
    public void handleKryoRequest(Connection connection, AbstractClientRequest request, GameContext gameContext) {
        AbstractServerResponse resp = handle(gameContext, request);
        if (resp != null && connection != null && connection.isConnected()) {
            connection.sendTCP(resp);
        }
    }
}