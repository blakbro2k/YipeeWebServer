package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.exceptions.ClientValidationException;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.services.impl.SecureSessionIdGenerator;
import asg.games.server.yipeewebserver.tools.NetUtil;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
    private static final Duration MAX_LIFETIME = Duration.ofHours(24);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(60);
    private static final String EXCEPTION_SESSION_MISSING = "SESSION_MISSING";
    private static final String EXCEPTION_SESSION_INVALID = "SESSION_INVALID";
    private static final String EXCEPTION_SESSION_MISMATCH = "SESSION_CLIENT_MISMATCH";
    private static final String EXCEPTION_SESSION_EXPIRED = "SESSION_EXPIRED";
    private static final String EXCEPTION_SESSION_IDLE = "SESSION_IDLE";

    private final ServerIdentity serverIdentity;
    private final YipeeClientConnectionRepository yipeeClientConnectionRepository;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final SecureSessionIdGenerator idGenerator;

    public PlayerConnectionEntity requireSession(String sessionId, String clientId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ClientValidationException(EXCEPTION_SESSION_MISSING, "X-Session-Id header is required.");
        }

        PlayerConnectionEntity conn = yipeeClientConnectionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ClientValidationException(EXCEPTION_SESSION_INVALID, "Invalid session."));

        if (!conn.getClientId().equals(clientId)) {
            throw new ClientValidationException(EXCEPTION_SESSION_MISMATCH, "Session does not belong to this client.");
        }

        Instant now = Instant.now();

        if (conn.getConnectedAt() != null &&
                Duration.between(conn.getConnectedAt(), now).compareTo(MAX_LIFETIME) > 0) {
            throw new ClientValidationException(EXCEPTION_SESSION_EXPIRED, "Session lifetime expired.");
        }

        if (conn.getLastActivity() != null &&
                Duration.between(conn.getLastActivity(), now).compareTo(IDLE_TIMEOUT) > 0) {
            throw new ClientValidationException(EXCEPTION_SESSION_IDLE, "Session idle timeout.");
        }

        conn.setLastActivity(now);
        yipeeClientConnectionRepository.save(conn);

        return conn;
    }

    // plus helpers to associate gameId, etc., as needed
    public ClientHandshakeResponse processClientHandshake(ClientHandshakeRequest request,
                                                          String ip,
                                                          String userAgent,
                                                          String provider) {

        // 1) Basic validation
        String playerId = request.getPlayerId();
        String clientId = request.getClientId();

        if (playerId == null || playerId.isBlank()) {
            throw new ClientValidationException("PLAYER_ID_MISSING", "playerId is required for handshake.");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new ClientValidationException("CLIENT_ID_MISSING", "clientId is required for handshake.");
        }

        YipeePlayer player = yipeePlayerRepository.findById(playerId).orElseThrow(
                () -> new IllegalArgumentException(playerId + " does not exist in database, Please register User."));

        log.debug("YipeePlayer: {}, retrieved from the database", player);

        // 2) Generate a new SecureRandom sessionId
        String sessionId = idGenerator.generateSessionId();
        Instant now = Instant.now();

        // 2b) Resolve external user id safely
        String externalUserId = request.getAuthToken();
        if (externalUserId == null || externalUserId.isBlank()) {
            // DEV/SAFE FALLBACK – tweak this to your taste
            externalUserId = "player:" + playerId; // or "client:" + clientId, etc.
        }

        // 3) Upsert PlayerConnectionEntity
        PlayerConnectionEntity conn = yipeeClientConnectionRepository
                .findByPlayerIdAndClientId(playerId, clientId)
                .orElseGet(PlayerConnectionEntity::new);

        conn.setName(player.getName());
        conn.setClientId(request.getClientId());
        conn.setSessionId(sessionId);
        conn.setProvider(provider);
        conn.setExternalUserId(externalUserId);
        conn.setPlayer(player);

        if (conn.getConnectedAt() == null) {
            conn.setConnectedAt(now);
        }
        conn.setIpAddress(ip);
        conn.setUserAgent(userAgent);
        conn.setLastActivity(now);
        conn.setConnectedAt(now);
        conn.setDisconnectedAt(null);
        yipeeClientConnectionRepository.save(conn);

        // 4) Build handshake response (add sessionId if not already there)
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        NetUtil.copyEnvelope(request, response);
        NetUtil.stampServerMeta(response, serverIdentity);
        response.setConnected(true);
        response.setServerTick(0); // or whatever initial tick makes sense
        response.setPlayerId(playerId);
        response.setSessionId(sessionId);

        return response;
    }
}