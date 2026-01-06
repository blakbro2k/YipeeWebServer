package asg.games.server.yipeewebserver.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class LaunchTokenService {

    private final SecretKey key;
    private final Duration ttl;

    public LaunchTokenService(
            @Value("${yipee.jwt.secret}") String secret,
            @Value("${yipee.launch.ttlSeconds:120}") long ttlSeconds
    ) {
        // IMPORTANT: for HS256, secret must be long enough (>= 32 bytes is a safe baseline).
        // If it's too short, JJWT will throw WeakKeyException.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String mintLaunchToken(String playerId,
                                  String playerName,
                                  int playerIcon,
                                  int playerRating,
                                  String clientId,
                                  String sessionId,
                                  String gameId,
                                  String tableId,
                                  int playerSeatIndex) {

        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        return Jwts.builder()
                .setSubject(playerId)                 // sub
                .setIssuedAt(Date.from(now))          // iat
                .setExpiration(Date.from(exp))        // exp
                .setId(UUID.randomUUID().toString())  // jti
                .claim("scope", "launch")
                .claim("pname", playerName)
                .claim("picon", playerIcon)
                .claim("prate", playerRating)
                .claim("cid", clientId)
                .claim("sid", sessionId)
                .claim("gid", gameId)
                .claim("tid", tableId)
                .claim("seatIndex", playerSeatIndex)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> verifyLaunchToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public record LaunchTokenContext(
            String playerId,
            String clientId,
            String sessionId,
            String gameId,
            String tableId,
            Integer seatIndex,
            String playerName,
            Integer playerIcon,
            Integer playerRating
    ) {}

    public LaunchTokenContext requireContext(String token) {
        log.debug("Enter requireContext(token={})", token);
        Claims c = verifyLaunchToken(token).getBody();

        String scope = c.get("scope", String.class);
        if (!"launch".equals(scope)) throw new IllegalArgumentException("Invalid token scope: " + scope);

        Number seat = c.get("seatIndex", Number.class);
        Number icon = c.get("picon", Number.class);
        Number rate = c.get("prate", Number.class);
        log.debug("seat={}", seat);
        log.debug("icon={}", icon);
        log.debug("rate={}", rate);

        LaunchTokenContext context = new LaunchTokenContext(
                c.getSubject(),
                c.get("cid", String.class),
                c.get("sid", String.class),
                c.get("gid", String.class),
                c.get("tid", String.class),
                seat == null ? null : seat.intValue(),
                c.get("pname", String.class),
                icon == null ? null : icon.intValue(),
                rate == null ? null : rate.intValue()
        );
        log.debug("Exit requireContext()={}", context);
        return context;
    }
}