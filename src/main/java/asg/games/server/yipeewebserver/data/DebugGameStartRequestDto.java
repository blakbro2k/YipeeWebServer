package asg.games.server.yipeewebserver.data;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class DebugGameStartRequestDto {
    private String clientId;
    private String sessionId;
    private String playerId;
    private String gameId;     // ID of the game/table to start
    private Integer seatIndex; // optional: if you want to tie it to a seat
    private long serverTick;
}