package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.config.WsLaunchTokenHandshakeInterceptor;
import asg.games.server.yipeewebserver.config.WsPacketRegistry;
import asg.games.server.yipeewebserver.core.GameContext;
import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.data.WsPacketEnvelope;
import asg.games.server.yipeewebserver.services.GameSessionService;
import asg.games.server.yipeewebserver.services.GameSessionTokenService;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.session.GameSession;
import asg.games.server.yipeewebserver.session.WebSocketSessionRegistry;
import asg.games.yipee.net.packets.AbstractClientRequest;
import asg.games.yipee.net.packets.AbstractServerResponse;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import asg.games.yipee.net.packets.GameStartRequest;
import asg.games.yipee.net.packets.GameSubscribeRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
import asg.games.yipee.net.packets.PlayerActionRequest;
import asg.games.yipee.net.packets.TableStateUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class YipeeWebSocketHandler extends TextWebSocketHandler {

    private final YipeePacketHandler packetHandler;
    private final GameContextFactory gameContextFactory;
    private final GameSessionService gameSessionService;
    private final WebSocketSessionRegistry wsRegistry;
    private final SessionService sessionService;
    private final GameSessionTokenService gameSessionTokenService;
    private final WsPacketRegistry wsPacketRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {}", session.getId());

        // If handshake interceptor populated launch claims, immediately bootstrap a session JWT
        Object playerId = session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_PLAYER_ID);
        Object clientId = session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_CLIENT_ID);
        Object sessionId = session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_SESSION_ID);
        Object gameId = session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_GAME_ID);
        Object tableId = session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_TABLE_ID);
        Object seatIndex = session.getAttributes().get(WsLaunchTokenHandshakeInterceptor.ATTR_SEAT_INDEX);

        if (playerId instanceof String pid && clientId instanceof String cid && sessionId instanceof String sid) {
            Integer sidx = (seatIndex instanceof Integer i) ? i : null;

            String jwt = gameSessionTokenService.mintGameSessionToken(
                    pid, cid, sid,
                    (gameId instanceof String g ? g : null),
                    (tableId instanceof String t ? t : null),
                    sidx
            );

            // Bind sessionId -> WS for server broadcasts
            wsRegistry.bind(sid, session);

            // Build a response packet (reflection-safe)
            Instant expiresAt = Instant.now().plusSeconds(60L * 60L * 4L); // match default TTL minutes=240
            Object resp = WsPacketFactory.gameAuthTokenResponse(
                    jwt, sid, cid, pid,
                    (gameId instanceof String g ? g : null),
                    (tableId instanceof String t ? t : null),
                    sidx,
                    expiresAt
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WS message from {}: {}", wsSession.getId(), payload);

        WsPacketEnvelope envelope = objectMapper.readValue(payload, WsPacketEnvelope.class);
        JsonNode node = envelope.getPayload();

        boolean isHandshake = "ClientHandshakeRequest".equals(envelope.getPacketType());

        /*AbstractClientRequest request = switch (envelope.getPacketType()) {
            case "ClientHandshakeRequest" -> objectMapper.treeToValue(node, ClientHandshakeRequest.class);
            case "GameSubscribeRequest" -> objectMapper.treeToValue(node, GameSubscribeRequest.class);
            case "GameStartRequest" -> objectMapper.treeToValue(node, GameStartRequest.class);
            case "PlayerActionRequest" -> objectMapper.treeToValue(node, PlayerActionRequest.class);
            case "MappedKeyUpdateRequest" -> objectMapper.treeToValue(node, MappedKeyUpdateRequest.class);
            case "TableStateUpdateRequest" -> objectMapper.treeToValue(node, TableStateUpdateRequest.class);

            default -> {
                log.warn("Unknown or unsupported packetType on WS: {}", envelope.getPacketType());
                wsSession.sendMessage(new TextMessage("{\"error\":\"unsupported packetType: " + envelope.getPacketType() + "\"}"));
                yield null;
            }
        };*/

        Class<? extends AbstractClientRequest> clazz = wsPacketRegistry.resolve(envelope.getPacketType());
        AbstractClientRequest request = objectMapper.treeToValue(envelope.getPayload(), clazz);

        if (request == null) {
            return;
        }

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
            gameSessionService.createMinimal(chr.getSessionId(), request.getClientId(), chr.getPlayerId());
        }

        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket error on {}: {}", session.getId(), exception.getMessage(), exception);
        super.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull org.springframework.web.socket.CloseStatus status) throws Exception {
        log.info("WebSocket closed: {} status={}", session.getId(), status);
        wsRegistry.unbind(session.getId());
        super.afterConnectionClosed(session, status);
    }
}
