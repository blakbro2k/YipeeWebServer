package asg.games.server.yipeewebserver.services;


import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.tools.NetUtil;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ServerIdentity serverIdentity;
    private final YipeeClientConnectionRepository yipeeClientConnectionRepository;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final SessionIdGenerator idGenerator;

    // Example: sessionId → clientId / playerId / gameId
    private final Map<String, String> sessionToClient = new ConcurrentHashMap<>();

    public String createSession(String clientId) {
        String sessionId = idGenerator.generateSessionId();
        sessionToClient.put(sessionId, clientId);
        return sessionId;
    }

    public String resolveClient(String sessionId) {
        return sessionToClient.get(sessionId);
    }

    // plus helpers to associate gameId, etc., as needed
    public ClientHandshakeResponse processClientHandshake(ClientHandshakeRequest request,
                                                          String ip,
                                                          String userAgent,
                                                          String provider) {
        log.debug("Handling ClientHandshakeResponse: {}", request);
        ClientHandshakeResponse resp = new ClientHandshakeResponse();
        NetUtil.copyEnvelope(request, resp);
        NetUtil.stampServerMeta(resp, serverIdentity);
        String playerId = request.getPlayerId();
        YipeePlayer player = yipeePlayerRepository.findById(playerId).orElseThrow(
                () -> new IllegalArgumentException(playerId + " does not exist in database, Please register User."));

        log.debug("YipeePlayer: {}, retrieved from the database", player);

        Instant now = Instant.now();
        String newSessionId = createSession(request.getClientId());

        PlayerConnectionDTO newPlayerConnection = new PlayerConnectionDTO();
        newPlayerConnection.setName(player.getName());
        newPlayerConnection.setClientId(request.getClientId());
        newPlayerConnection.setSessionId(newSessionId);
        newPlayerConnection.setProvider(provider);
        newPlayerConnection.setExternalUserId(request.getAuthToken());
        newPlayerConnection.setPlayer(player);
        newPlayerConnection.setConnected(true);
        newPlayerConnection.setIpAddress(ip);
        newPlayerConnection.setUserAgent(userAgent);
        newPlayerConnection.setLastActivity(now);
        newPlayerConnection.setConnectedAt(now);
        newPlayerConnection.setDisconnectedAt(null);
        yipeeClientConnectionRepository.save(newPlayerConnection);

        resp.setConnected(true);
        resp.setServerTick(0); // or whatever initial tick makes sense
        resp.setPlayerId(playerId);
        resp.setSessionId(newSessionId);

        return resp;
    }
}