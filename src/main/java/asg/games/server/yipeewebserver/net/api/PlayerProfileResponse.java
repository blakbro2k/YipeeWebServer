package asg.games.server.yipeewebserver.net.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@AllArgsConstructor
public class PlayerProfileResponse {
    private String playerId;
    private String name;
    private int icon;
    private int rating;
    private String sessionId;
}