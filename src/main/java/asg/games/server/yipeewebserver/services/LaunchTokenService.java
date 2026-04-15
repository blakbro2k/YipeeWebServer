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

    private final SecretKey apikey;
    private final SecretKey launchkey;
    private final Duration ttl;
    private final long apiTtlSeconds;
    private final String issuer;

    public LaunchTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.launch.secret}") String launchSecret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.ttlSeconds}") long apiTtlSeconds,
            @Value("${security.jwt.launch.ttlSeconds}") long ttlSeconds
    ) {
        // IMPORTANT: for HS256, secret must be long enough (>= 32 bytes is a safe baseline).
        // If it's too short, JJWT will throw WeakKeyException.
        this.apikey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.launchkey = Keys.hmacShaKeyFor(launchSecret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.issuer = issuer;
        this.apiTtlSeconds = apiTtlSeconds;
    }

    public String mintApiToken(String playerId,
                               String playerName,
                               int playerIcon,
                               int playerRating,
                               String clientId,
                               String sessionId) {

        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofSeconds(apiTtlSeconds));

        return Jwts.builder()
                .setSubject(playerId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .setId(UUID.randomUUID().toString())
                .setIssuer(issuer)
                .claim("scope", "api")
                .claim("pname", playerName)
                .claim("picon", playerIcon)
                .claim("prate", playerRating)
                .claim("cid", clientId)
                .claim("sid", sessionId)
                .signWith(apikey, SignatureAlgorithm.HS256)
                .compact();
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

        String token = Jwts.builder()
                .setSubject(playerId)                 // sub
                .setIssuedAt(Date.from(now))          // iat
                .setExpiration(Date.from(exp))        // exp
                .setId(UUID.randomUUID().toString())  // jti
                .setIssuer(issuer)
                .claim("scope", "launch")
                .claim("pname", playerName)
                .claim("picon", playerIcon)
                .claim("prate", playerRating)
                .claim("cid", clientId)
                .claim("sid", sessionId)
                .claim("gid", gameId)
                .claim("tid", tableId)
                .claim("seatIndex", playerSeatIndex)
                .signWith(launchkey, SignatureAlgorithm.HS256)
                .compact();
        log.debug("Minted launch token prefix={}", token.substring(0, Math.min(30, token.length())));
        return token;
    }

    public Jws<Claims> verifyLaunchToken(String token) {
        return Jwts.parserBuilder()
                .requireIssuer(issuer)
                .setSigningKey(launchkey)
                .build()
                .parseClaimsJws(token);
    }

    public Jws<Claims> verifyAPIToken(String token) {
        return Jwts.parserBuilder()
                .requireIssuer(issuer)
                .setSigningKey(apikey)
                .build()
                .parseClaimsJws(token);
    }

    private static String jwtHeader(String token) {
        try {
            String headerB64 = token.split("\\.")[0];
            byte[] raw = java.util.Base64.getUrlDecoder().decode(headerB64);
            return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<unreadable>";
        }
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
        log.debug("Enter requireContext(tokenPrefix={})", token == null ? null : token.substring(0, Math.min(16, token.length())));
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