package asg.games.server.yipeewebserver.net;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ConnectionContext {
    public String clientId;
    public String sessionId;
    public String gameId;
    public String playerId;
    public String serverId;
}