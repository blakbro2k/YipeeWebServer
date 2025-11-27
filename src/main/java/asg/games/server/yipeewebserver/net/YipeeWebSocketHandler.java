package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.data.WsPacketEnvelope;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import asg.games.yipee.net.packets.DisconnectRequest;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("WS message from {}: {}", session.getId(), payload);

        WsPacketEnvelope envelope = objectMapper.readValue(payload, WsPacketEnvelope.class);
        JsonNode node = envelope.getPayload();

        switch (envelope.getPacketType()) {
            case "ClientHandshakeRequest" -> {
                ClientHandshakeRequest req = objectMapper.treeToValue(node, ClientHandshakeRequest.class);
                ClientHandshakeResponse resp = packetHandler.processClientHandshake(req, "", "", "");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
            }
            case "DisconnectRequest" -> {
                DisconnectRequest req = objectMapper.treeToValue(node, DisconnectRequest.class);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        packetHandler.processDisconnect(req)
                )));
            }
            case "MappedKeyUpdateRequest" -> {
                MappedKeyUpdateRequest req = objectMapper.treeToValue(node, MappedKeyUpdateRequest.class);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        packetHandler.processMappedKeyUpdate(req)
                )));
            }
            case "TableStateUpdateRequest" -> {
                TableStateUpdateRequest req = objectMapper.treeToValue(node, TableStateUpdateRequest.class);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        packetHandler.processTableStateUpdate(req)
                )));
            }
            default -> {
                log.warn("Unknown packetType: {}", envelope.getPacketType());
                session.sendMessage(new TextMessage("{\"error\":\"Unknown packetType\"}"));
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) throws Exception {
        log.error("WebSocket error on {}: {}", session.getId(), exception.getMessage(), exception);
        // Optionally use packetHandler.processNetError(...) here with a null Connection
    }
}
