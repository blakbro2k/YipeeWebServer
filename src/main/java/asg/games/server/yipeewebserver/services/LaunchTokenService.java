package asg.games.server.yipeewebserver.services;

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
public class LaunchTokenService {

    private final SecretKey key;
    private final Duration ttl;

    public LaunchTokenService(
            @Value("${security.jwt.secret}") String secret,
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
                                  String tableId) {

        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        return Jwts.builder()
                .setSubject(playerId)                 // sub
                .setIssuedAt(Date.from(now))          // iat
                .setExpiration(Date.from(exp))        // exp
                .setId(UUID.randomUUID().toString())  // jti
                .claim("scope", "launch")
                .claim("pname", playerName)
                .claim("picon", 3)
                .claim("prate", 1500)
                .claim("cid", clientId)
                .claim("sid", sessionId)
                .claim("gid", gameId)
                .claim("tid", tableId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> verifyLaunchToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }
}