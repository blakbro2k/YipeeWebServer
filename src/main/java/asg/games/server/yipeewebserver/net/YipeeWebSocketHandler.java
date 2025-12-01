package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.data.WsPacketEnvelope;
import asg.games.yipee.net.packets.AbstractClientRequest;
import asg.games.yipee.net.packets.AbstractServerResponse;
import asg.games.yipee.net.packets.GameStartRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
import asg.games.yipee.net.packets.PlayerActionRequest;
import asg.games.yipee.net.packets.TableStateUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
        // If you want, you can stash sessionId ↔ session in a map here
        // for pushing broadcasts later.
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WS message from {}: {}", session.getId(), payload);

        WsPacketEnvelope envelope = objectMapper.readValue(payload, WsPacketEnvelope.class);
        JsonNode node = envelope.getPayload();

        AbstractClientRequest request = switch (envelope.getPacketType()) {
            case "GameStartRequest" -> objectMapper.treeToValue(node, GameStartRequest.class);
            case "PlayerActionRequest" -> objectMapper.treeToValue(node, PlayerActionRequest.class);
            case "MappedKeyUpdateRequest" -> objectMapper.treeToValue(node, MappedKeyUpdateRequest.class);
            case "TableStateUpdateRequest" -> objectMapper.treeToValue(node, TableStateUpdateRequest.class);
            default -> {
                log.warn("Unknown or unsupported packetType on WS: {}", envelope.getPacketType());
                session.sendMessage(new TextMessage(
                        "{\"error\":\"Unknown or unsupported packetType: " + envelope.getPacketType() + "\"}"));
                yield null;
            }
        };

        if (request == null) {
            return; // already responded with error
        }

        // Let YipeePacketHandler do the transport-agnostic work
        AbstractServerResponse response = packetHandler.handle(gameContextFactory.fromWebSocket(session, request), request);

        // You can either:
        // 1) send the plain response, or
        // 2) re-wrap it in a WsPacketEnvelope with a "responseType" if you prefer
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
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