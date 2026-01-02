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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

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
    private final GameSessionTokenService gameSessionTokenService;

    public record ResolvedSession(
            String playerId,
            String clientId,
            String sessionId,
            String gameId,
            String tableId,
            Integer seatIndex
    ) {}

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

    public PlayerConnectionEntity upsertFromLaunchClaims(String sessionId, String clientId, String playerId) {
        if (sessionId == null || sessionId.isBlank()) throw new ClientValidationException("SESSION_MISSING", "sid missing");
        if (clientId == null || clientId.isBlank()) throw new ClientValidationException("CLIENT_ID_MISSING", "cid missing");
        if (playerId == null || playerId.isBlank()) throw new ClientValidationException("PLAYER_ID_MISSING", "sub missing");

        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new ClientValidationException("PLAYER_NOT_FOUND", "player missing"));

        PlayerConnectionEntity conn = yipeeClientConnectionRepository
                .findByPlayerIdAndClientId(playerId, clientId)
                .orElseGet(PlayerConnectionEntity::new);

        conn.setPlayer(player);
        conn.setClientId(clientId);
        conn.setSessionId(sessionId);

        Instant now = Instant.now();
        if (conn.getConnectedAt() == null) conn.setConnectedAt(now);
        conn.setLastActivity(now);
        conn.setDisconnectedAt(null);

        return yipeeClientConnectionRepository.save(conn);
    }

    public ResolvedSession resolveFromRequest(asg.games.yipee.net.packets.AbstractClientRequest request) {

        String clientId = request.getClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new ClientValidationException(EXCEPTION_SESSION_MISSING, "clientId is required.");
        }

        // If authToken exists, validate it as a game_session JWT
        String authToken = request.getAuthToken();
        if (authToken != null && !authToken.isBlank()) {
            Jws<Claims> jws = gameSessionTokenService.verifyGameSessionToken(authToken);
            Claims c = jws.getBody();

            String scope = c.get("scope", String.class);
            if (!"game_session".equals(scope)) {
                throw new ClientValidationException(EXCEPTION_SESSION_INVALID, "Invalid authToken scope.");
            }

            String tokenClientId = c.get("cid", String.class);
            if (tokenClientId != null && !tokenClientId.equals(clientId)) {
                throw new ClientValidationException(EXCEPTION_SESSION_MISMATCH, "authToken clientId mismatch.");
            }

            String playerId = c.getSubject();
            String sessionId = c.get("sid", String.class);

            // Optional: still enforce DB session existence (revocation/idle timeout)
            // This uses your existing requireSession() implementation.
            requireSession(sessionId, clientId);

            return new ResolvedSession(
                    playerId,
                    clientId,
                    sessionId,
                    c.get("gid", String.class),
                    c.get("tid", String.class),
                    c.get("seatIndex", Integer.class)
            );
        }

        // Fallback: old-school sessionId + clientId
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new ClientValidationException(EXCEPTION_SESSION_MISSING, "sessionId is required.");
        }

        PlayerConnectionEntity conn = requireSession(sessionId, clientId);

        return new ResolvedSession(
                conn.getPlayer() == null ? null : conn.getPlayer().getId(),
                clientId,
                sessionId,
                null,
                null,
                null
        );
    }
}