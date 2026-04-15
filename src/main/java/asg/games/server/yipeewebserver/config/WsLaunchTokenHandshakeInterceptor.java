package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.services.LaunchTokenService;
import asg.games.server.yipeewebserver.services.SessionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsLaunchTokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_PLAYER_ID = "playerId";
    public static final String ATTR_CLIENT_ID = "clientId";
    public static final String ATTR_SESSION_ID = "sessionId";
    public static final String ATTR_GAME_ID   = "gameId";
    public static final String ATTR_TABLE_ID  = "tableId";
    public static final String ATTR_SEAT_INDEX = "seatIndex";

    private final LaunchTokenService launchTokenService;
    private final SessionService sessionService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        log.debug("beforeHandshake()");
        String launchToken = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("launchToken");

        if (launchToken == null || launchToken.isBlank()) {
            log.warn("WS handshake rejected: missing token");
            return false;
        }

        Jws<Claims> jws;
        try {
            jws = launchTokenService.verifyLaunchToken(launchToken);
        } catch (Exception e) {
            log.warn("WS handshake rejected: invalid token: {}", e.getMessage());
            return false;
        }

        Claims c = jws.getBody();
        String scope = c.get("scope", String.class);
        if (!"launch".equals(scope)) {
            log.warn("WS handshake rejected: wrong scope={}", scope);
            return false;
        }

        String playerId = c.getSubject();                 // sub
        String clientId = c.get("cid", String.class);
        String sessionId = c.get("sid", String.class);
        String gameId   = c.get("gid", String.class);
        String tableId  = c.get("tid", String.class);
        Number seatNum = c.get(ATTR_SEAT_INDEX, Number.class);
        Integer seatIndex = seatNum == null ? null : seatNum.intValue();

        // Mint/Upsert a DB-valid session for life of the game (or until idle timeout rules)
        sessionService.upsertFromLaunchClaims(sessionId, clientId, playerId);

        attributes.put(ATTR_PLAYER_ID, playerId);
        attributes.put(ATTR_CLIENT_ID, clientId);
        attributes.put(ATTR_SESSION_ID, sessionId);
        attributes.put(ATTR_GAME_ID, gameId);
        attributes.put(ATTR_TABLE_ID, tableId);
        attributes.put(ATTR_SEAT_INDEX, seatIndex);
        log.debug("beforeHandshake()={}", true);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
        log.debug("afterHandshake()=request={}", request);
        log.debug("afterHandshake()=response={}", response);
        log.debug("afterHandshake()=wsHandler={}", wsHandler);
    }
}


