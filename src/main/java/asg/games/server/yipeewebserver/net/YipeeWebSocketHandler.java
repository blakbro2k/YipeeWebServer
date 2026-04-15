package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.config.WsLaunchTokenHandshakeInterceptor;
import asg.games.server.yipeewebserver.config.WsPacketRegistry;
import asg.games.server.yipeewebserver.core.GameContext;
import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.data.WsPacketEnvelope;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.GameSessionService;
import asg.games.server.yipeewebserver.services.GameSessionTokenService;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.services.TableService;
import asg.games.server.yipeewebserver.session.GameSession;
import asg.games.server.yipeewebserver.session.WebSocketSessionRegistry;
import asg.games.yipee.common.net.wire.AbstractClientRequest;
import asg.games.yipee.common.net.wire.AbstractServerResponse;
import asg.games.yipee.common.net.wire.ClientHandshakeResponse;
import asg.games.yipee.common.net.wire.GameAuthTokenResponse;
import asg.games.yipee.common.net.wire.GameStartRequest;
import asg.games.yipee.common.net.wire.GameSubscribeRequest;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.tools.Util;
import asg.games.yipee.net.tools.NetUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class YipeeWebSocketHandler extends TextWebSocketHandler {
    private final YipeeTableRepository yipeeTableRepository;
    private final YipeePlayerRepository yipeePlayerRepository;

    private final YipeePacketHandler packetHandler;
    private final GameContextFactory gameContextFactory;
    private final GameSessionService gameSessionService;
    private final TableService tableService;
    private final WebSocketSessionRegistry wsRegistry;
    private final SessionService sessionService;
    private final GameSessionTokenService gameSessionTokenService;
    private final WsPacketRegistry wsPacketRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServerIdentity serverIdentity;
    public static final String ATTR_BOUND_SESSION_ID = "boundSessionId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.debug("Enter afterConnectionEstablished()");
        log.info("WebSocket connected: {}", session.getId());

        // If handshake interceptor populated launch claims, immediately bootstrap a session JWT
        String playerId = Util.otos(session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_PLAYER_ID));
        String clientId = Util.otos(session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_CLIENT_ID));
        String sessionId = Util.otos(session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_SESSION_ID));
        String gameId = Util.otos(session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_GAME_ID));
        String tableId = Util.otos(session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_TABLE_ID));
        int seatIndex = Util.otoi(session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_SEAT_INDEX));

        boolean hasLaunch = playerId != null && !playerId.isBlank()
                && clientId != null && !clientId.isBlank()
                && sessionId != null && !sessionId.isBlank();

        if (!hasLaunch) {
            log.info("WS connected without launch claims; awaiting ClientHandshakeRequest. ws={}", session.getId());
            return;
        }
        String gameToken = gameSessionTokenService.mintGameSessionToken(
                playerId, clientId, sessionId,
                gameId,
                tableId,
                seatIndex
        );

        // Bind sessionId -> WS for server broadcasts
        wsRegistry.bind(sessionId, session);
        session.getAttributes().put(ATTR_BOUND_SESSION_ID, sessionId);

        gameSessionService.touchOrCreateMinimal(sessionId, clientId); // recommended

        // Build a response packet (reflection-safe)
        Instant expiresAt = Instant.now().plusSeconds(60L * 60L * 4L); // match default TTL minutes=240

        TableService.TableContext tableContext = tableService.getTableContext(tableId);

        YipeePlayer player = yipeePlayerRepository.findById(playerId).orElse(null);
        // Get Player details
        String name = (player == null? null : player.getName());
        int icon = (player == null? -1 : player.getIcon());
        int rating = (player == null? -1 : player.getRating());

        GameAuthTokenResponse response = NetUtil.newGameAuthTokenResponse(
                playerId,
                name,
                icon,
                rating,
                clientId,
                tableId,
                tableContext.roomId(),
                tableContext.roomName(),
                tableContext.loungeName(),
                seatIndex,
                expiresAt.toString(),
                gameToken,
                serverIdentity.getServerId(),
                gameId,
                sessionId,
                -1,
                serverIdentity.getServerTimeStamp(),
                serverIdentity.getTickRate()
        );

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        log.debug("Exit afterConnectionEstablished()");
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        log.debug("Enter handleTextMessage(wsSession={}, message={})", wsSession, message);
        String payload = message.getPayload();
        log.debug("WS message from {}: {}", wsSession.getId(), payload);

        WsPacketEnvelope envelope = objectMapper.readValue(payload, WsPacketEnvelope.class);
        Class<? extends AbstractClientRequest> clazz = wsPacketRegistry.resolve(envelope.getPacketType());
        if (clazz == null) {
            log.warn("Unknown packetType on WS: {}", envelope.getPacketType());
            wsSession.sendMessage(new TextMessage("{\"error\":\"unsupported packetType: " + envelope.getPacketType() + "\"}"));
            return;
        }

        AbstractClientRequest request = objectMapper.treeToValue(envelope.getPayload(), clazz);
        if (request instanceof GameStartRequest) {
            log.info("GameStartRequest received from ws={}", wsSession.getId());

            // TEMP: send a debug gamestate
            String debugState = "{\"packetType\":\"GameStateUpdateResponse\",\"payload\":{\"debug\":\"hello\"}}";

            wsSession.sendMessage(new TextMessage(debugState));
            return;
        }

        boolean isHandshake = clazz.getSimpleName().equals("ClientHandshakeRequest");

        // Central auth/session resolution (JWT if present; fallback to sessionId/clientId)
        SessionService.ResolvedSession resolved = null;
        if (!isHandshake) {
            try {
                resolved = sessionService.resolveFromRequest(request);
            } catch (Exception e) {
                wsSession.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}"));
                return;
            }

            // keep ws bindings for server broadcasts
            wsRegistry.bind(resolved.sessionId(), wsSession);
            wsSession.getAttributes().put(ATTR_BOUND_SESSION_ID, resolved.sessionId());
        }

        // Existing behavior: manage minimal game session (optional)
        GameSession gs = null;
        if (!isHandshake) {
            gs = gameSessionService.touchOrCreateMinimal(resolved.sessionId(), resolved.clientId());

            // optional bind game based on packets
            if (request instanceof GameSubscribeRequest sub && sub.getGameId() != null && !sub.getGameId().isBlank()) {
                gs = gameSessionService.bindGame(gs.sessionId(), gs.clientId(), sub.getGameId());
            }
            if (request instanceof GameStartRequest start && start.getGameId() != null && !start.getGameId().isBlank()) {
                gs = gameSessionService.bindGame(gs.sessionId(), gs.clientId(), start.getGameId());
            }
        }
        log.debug("gs {}:", gs);

        GameContext ctx = gameContextFactory.fromGameSession(gs, request);

        AbstractServerResponse response;
        try {
            response = packetHandler.handle(ctx, request);
        } catch (Exception e) {
            log.error("WS packet handling failed: {}", e.getMessage(), e);
            wsSession.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}"));
            return;
        }

        // If handshake response carries session, bind it
        if (isHandshake && response instanceof ClientHandshakeResponse chr) {
            wsRegistry.bind(chr.getSessionId(), wsSession);
            wsSession.getAttributes().put(ATTR_BOUND_SESSION_ID, chr.getSessionId());
            gameSessionService.createMinimal(chr.getSessionId(), request.getClientId(), chr.getPlayerId());
        }

        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        log.debug("Exit handleTextMessage()");
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket error on {}: {}", session.getId(), exception.getMessage(), exception);
        super.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket closed: {} status={}", session.getId(), status);

        String boundSessionId = Util.otos(session.getAttributes().get(ATTR_BOUND_SESSION_ID));
        if (boundSessionId != null && !boundSessionId.isBlank()) {
            wsRegistry.unbind(boundSessionId);
        } else {
            wsRegistry.unbind(session.getId()); // optional fallback
        }
        super.afterConnectionClosed(session, status);
    }
}
