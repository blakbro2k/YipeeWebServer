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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InvalidObjectException;

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
                dto.getSessionId(),
                dto.getServerTick()
        );

        GameStartResponse response = YipeePacketHandler.getClassResponse(GameStartResponse.class, packetHandler.handle(ctx, packet));

        return ResponseEntity.ok(response);
    }
}
