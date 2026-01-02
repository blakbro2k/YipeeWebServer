package asg.games.server.yipeewebserver.services;

import asg.games.server.yipeewebserver.session.GameSession;
import asg.games.yipee.net.errors.YipeeSessionException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class GameSessionTokenService {

    private final SecretKey key;
    private final Duration ttl;

    public GameSessionTokenService(
            @Value("${yipee.jwt.secret}") String secret,
            @Value("${yipee.jwt.gameSessionTtlMinutes}") long ttlMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String mintGameSessionToken(String playerId,
                                       String clientId,
                                       String sessionId,
                                       String gameId,
                                       String tableId,
                                       Integer seatIndex) {

        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        return Jwts.builder()
                .setSubject(playerId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .setId(UUID.randomUUID().toString())
                .claim("scope", "game_session")
                .claim("cid", clientId)
                .claim("sid", sessionId)
                .claim("gid", gameId)
                .claim("tid", tableId)
                .claim("seatIndex", seatIndex)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> verifyGameSessionToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public record GameSessionTokenContext(
            String playerId,
            String clientId,
            String sessionId,
            String gameId,
            String tableId,
            Integer seatIndex
    ) {}

    public GameSessionTokenContext requireContext(String token) {
        Claims c = verifyGameSessionToken(token).getBody();

        // scope guard (optional but recommended)
        String scope = c.get("scope", String.class);
        if (!"game_session".equals(scope)) {
            throw new IllegalArgumentException("Invalid token scope: " + scope);
        }

        Number n = c.get("seatIndex", Number.class);

        return new GameSessionTokenContext(
                c.getSubject(),
                c.get("cid", String.class),
                c.get("sid", String.class),
                c.get("gid", String.class),
                c.get("tid", String.class),
                ((n == null) ? null : n.intValue())
        );
    }

}