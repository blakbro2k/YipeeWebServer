package asg.games.server.yipeewebserver.net;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@AllArgsConstructor
public class RegisterPlayerRequest {
    private String playerName;
    private int icon;
    private int rating;
    private String clientId;
}