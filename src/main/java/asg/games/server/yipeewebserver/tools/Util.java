package asg.games.server.yipeewebserver.tools;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.yipee.net.packets.AbstractClientRequest;
import asg.games.yipee.net.packets.AbstractServerResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Util {



    // ========================================================================
    //  Utility: envelope + meta
    // ========================================================================

    public static void copyEnvelope(AbstractClientRequest req, AbstractServerResponse resp) {
        resp.setGameId(req.getGameId());
        resp.setSessionId(req.getSessionId());
        // serverId / tick / timestamp / tickRate are filled by stampServerMeta
    }

    public static void stampServerMeta(AbstractServerResponse resp, ServerIdentity serverIdentity) {
        resp.setServerId(serverIdentity.getFullId());
        resp.setServerTimestamp(System.currentTimeMillis());
        // Leave serverTick/tickRate to the caller if you want real values,
        // or keep them 0 for now (like your handshake).
    }
}
