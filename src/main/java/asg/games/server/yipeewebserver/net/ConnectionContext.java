package asg.games.server.yipeewebserver.net;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Small value object to keep track of per-connection meta.
 * You likely already have this defined elsewhere; if so, reuse that.
 */

@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Getter
@Setter
public class ConnectionContext {
    public String clientId;
    public String sessionId;
    public String gameId;
    public String playerId;
}