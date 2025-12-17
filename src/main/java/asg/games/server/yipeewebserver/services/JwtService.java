package asg.games.server.yipeewebserver.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final String secret;
    private final String issuer;
    private final long ttlSeconds;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.ttlSeconds}") long ttlSeconds
    ) {
        this.secret = secret;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String mint(String playerId, String username, int rating, int icon) {
        long now = System.currentTimeMillis() / 1000;

        // Use Nimbus directly (already present via resource-server starter)
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                .subject(playerId)
                .issuer(issuer)
                .issueTime(new java.util.Date(now * 1000))
                .expirationTime(new java.util.Date((now + ttlSeconds) * 1000))
                .claim("username", username)
                .claim("rating", rating)
                .claim("icon", icon)
                .build();

        try {
            var signer = new com.nimbusds.jose.crypto.MACSigner(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var jwsHeader = new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256);
            var jwt = new com.nimbusds.jwt.SignedJWT(jwsHeader, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint JWT", e);
        }
    }
}