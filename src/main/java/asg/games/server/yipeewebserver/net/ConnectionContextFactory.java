package asg.games.server.yipeewebserver.net;

import asg.games.yipee.net.packets.AbstractClientRequest;
import com.esotericsoftware.kryonet.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InvalidObjectException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionContextFactory {
    private final Map<String, String> sessionToGameId = new ConcurrentHashMap<>();
    private final Map<Integer, String> connectionToGameIdx = new ConcurrentHashMap<>();
    private final Map<Integer, ConnectionContext> connectionContexts = new ConcurrentHashMap<>();

    public ConnectionContext updateConnectionContext(Connection connection, Object object) throws InvalidObjectException {
        if (!(object instanceof AbstractClientRequest req)) throw new InvalidObjectException("Packet recieved was not of type: AbstractClientRequest");

        ConnectionContext connectionContext = connectionContexts.computeIfAbsent(
                connection.getID(),
                id -> new ConnectionContext()
        );

        connectionContext.clientId = req.getClientId();
        connectionContext.sessionId = req.getSessionId();

        // gameId will be resolved by server after lookup:
        connectionContext.gameId = findGameIdForSession(req.getSessionId());
        return connectionContext;
    }

    public ConnectionContext getConnectionContextByConnectionId(int connectionId) {
        return connectionContexts.get(connectionId);
    }

    private String findGameIdForSession(String sessionId) {
        if (sessionId == null) return null;
        return sessionToGameId.get(sessionId);
    }
}