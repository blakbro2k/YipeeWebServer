package asg.games.server.yipeewebserver.controllers;

import asg.games.server.yipeewebserver.core.GameContext;
import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.data.DebugGameStartRequestDto;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.yipee.net.packets.GameStartRequest;
import asg.games.yipee.net.packets.GameStartResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Profile({"dev", "test"}) // <-- ONLY active in dev/test
public class YipeeDebugController {
    private final YipeePacketHandler packetHandler;
    private final GameContextFactory gameContextFactory;

    @PostMapping("/game/start")
    public ResponseEntity<GameStartResponse> debugStartGame(
            @RequestBody DebugGameStartRequestDto dto) throws InvalidObjectException {

        log.info("DEBUG startGame request: {}", dto);

        // Build the internal packet
        GameStartRequest packet = new GameStartRequest();
        packet.setClientId(dto.getClientId());
        packet.setSessionId(dto.getSessionId());
        packet.setPlayerId(dto.getPlayerId());
        packet.setReady(true);
        // packet.setSeatIndex(dto.getSeatIndex()); // if your packet supports it
        // packet.setGameId(dto.getGameId());       // if needed

        // Resolve game context same way transport handlers do
        GameContext ctx = gameContextFactory.fromIds(
                dto.getGameId(),
                dto.getPlayerId(),
                dto.getClientId(),
                dto.getSessionId(),
                dto.getServerTick()
        );

        WebSocketSession session = getNewWebsocketSession();

        GameStartResponse response = YipeePacketHandler.getClassResponse(GameStartResponse.class, packetHandler.handle(ctx, packet));

        return ResponseEntity.ok(response);
    }

    private WebSocketSession getNewWebsocketSession() {
        return new WebSocketSession() {
            @Override
            public String getId() {
                return "";
            }

            @Override
            public URI getUri() {
                return null;
            }

            @Override
            public HttpHeaders getHandshakeHeaders() {
                return null;
            }

            @Override
            public Map<String, Object> getAttributes() {
                return Map.of();
            }

            @Override
            public Principal getPrincipal() {
                return null;
            }

            @Override
            public InetSocketAddress getLocalAddress() {
                return null;
            }

            @Override
            public InetSocketAddress getRemoteAddress() {
                return null;
            }

            @Override
            public String getAcceptedProtocol() {
                return "";
            }

            @Override
            public void setTextMessageSizeLimit(int messageSizeLimit) {

            }

            @Override
            public int getTextMessageSizeLimit() {
                return 0;
            }

            @Override
            public void setBinaryMessageSizeLimit(int messageSizeLimit) {

            }

            @Override
            public int getBinaryMessageSizeLimit() {
                return 0;
            }

            @Override
            public List<WebSocketExtension> getExtensions() {
                return List.of();
            }

            @Override
            public void sendMessage(WebSocketMessage<?> message) throws IOException {

            }

            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public void close() throws IOException {

            }

            @Override
            public void close(CloseStatus status) throws IOException {

            }
        };
    }
}
