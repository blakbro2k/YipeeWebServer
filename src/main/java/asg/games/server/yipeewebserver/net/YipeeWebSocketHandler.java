package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.core.GameContext;
import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.data.WsPacketEnvelope;
import asg.games.server.yipeewebserver.services.GameSessionService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class YipeeWebSocketHandler extends TextWebSocketHandler {
    private final YipeePacketHandler packetHandler;
    private final GameContextFactory gameContextFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameSessionService gameSessionService;
    private final WebSocketSessionRegistry wsRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
        // If you want, you can stash sessionId ↔ session in a map here
        // for pushing broadcasts later.
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WS message from {}: {}", wsSession.getId(), payload);

        WsPacketEnvelope envelope = objectMapper.readValue(payload, WsPacketEnvelope.class);
        JsonNode node = envelope.getPayload();

        AbstractClientRequest request = switch (envelope.getPacketType()) {
            case "ClientHandshakeRequest" -> objectMapper.treeToValue(node, ClientHandshakeRequest.class);
            case "GameSubscribeRequest" -> objectMapper.treeToValue(node, GameSubscribeRequest.class);
            case "GameStartRequest" -> objectMapper.treeToValue(node, GameStartRequest.class);
            case "PlayerActionRequest" -> objectMapper.treeToValue(node, PlayerActionRequest.class);
            case "MappedKeyUpdateRequest" -> objectMapper.treeToValue(node, MappedKeyUpdateRequest.class);
            case "TableStateUpdateRequest" -> objectMapper.treeToValue(node, TableStateUpdateRequest.class);
            default -> {
                log.warn("Unknown or unsupported packetType on WS: {}", envelope.getPacketType());
                wsSession.sendMessage(new TextMessage("{\"error\":\"Unknown or unsupported packetType: " + envelope.getPacketType() + "\"}"));
                yield null;
            }
        };

        if (request == null) {
            return; // already responded with error
        }

        boolean isHandshake = request instanceof ClientHandshakeRequest;

        // 1) Validate session for all non-handshake requests
        GameSession gs = null;

        if (!isHandshake) {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                wsSession.sendMessage(new TextMessage("{\"error\":\"Missing sessionId.\"}"));
                return;
            }

            gs = gameSessionService.touchOrCreateMinimal(sessionId, request.getClientId());
            wsRegistry.bind(sessionId, wsSession);

            // Optional: bind game when these packets arrive (depends on your flow)
            if (request instanceof GameSubscribeRequest sub && sub.getGameId() != null && !sub.getGameId().isBlank()) {
                gs = gameSessionService.bindGame(gs.sessionId(), gs.clientId(), sub.getGameId());
            }
            if (request instanceof GameStartRequest start && start.getGameId() != null && !start.getGameId().isBlank()) {
                gs = gameSessionService.bindGame(gs.sessionId(), gs.clientId(), start.getGameId());
            }
        }

        // 2) Build *resolved* context (use session bindings, not WS attributes)
        //var resolved = gameSessionService.resolveForRequest(request); // returns playerId, gameId, sessionId, clientId
        GameContext ctx = gameContextFactory.fromGameSession(gs, request);  // assembler-only

        // 3) Handle
        // Let YipeePacketHandler do the transport-agnostic work
        AbstractServerResponse response = packetHandler.handle(ctx, request);

        // 4) If handshake created a sessionId, bind it now
        if (isHandshake && response instanceof ClientHandshakeResponse chr) {
            wsRegistry.bind(chr.getSessionId(), wsSession);
            gameSessionService.createMinimal(chr.getSessionId(), request.getClientId(), chr.getPlayerId());
        }

        // You can either:
        // 1) send the plain response, or
        // 2) re-wrap it in a WsPacketEnvelope with a "responseType" if you prefer
        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket error on {}: {}", session.getId(), exception.getMessage(), exception);
        // Optionally: build a simple error JSON here or just close the session
        // If you really want to reuse ErrorResponse, you can construct one manually,
        // but packetHandler.processNetError() expects a Kryo Connection so we
        // treat WS separately.
    }
}