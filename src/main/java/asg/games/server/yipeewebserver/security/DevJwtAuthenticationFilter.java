package asg.games.server.yipeewebserver.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

/**
 * Dev-only JWT filter.
 * Accepts tokens of the form header.payload.signature with:
 *   sub      = playerId (DB UUID)
 *   username = display name
 *   rating   = numeric rating
 *   icon     = icon key/URL
 *
 * No signature verification. DO NOT use in production.
 */
@Slf4j
public class DevJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                JwtIdentity identity = parseJwt(token);
                if (identity != null && identity.playerId() != null && !identity.playerId().isEmpty()) {
                    // principal = playerId; authorities = empty for now
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    identity.playerId(),
                                    null,
                                    Collections.emptyList()
                            );
                    // Stash extra info (username/rating/icon) in details if you want it later
                    auth.setDetails(identity);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Dev mode: don't block the request, just log
                System.err.println("DevJwtAuthenticationFilter: failed to parse token: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private JwtIdentity parseJwt(String token) throws IOException {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        String payloadBase64 = parts[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payloadBase64);
        String json = new String(decoded, StandardCharsets.UTF_8);

        JsonNode node = objectMapper.readTree(json);

        String playerId = node.path("sub").asText(null);        // DB ID
        String username = node.path("username").asText(null);   // display name
        Integer rating  = node.hasNonNull("rating") ? node.get("rating").asInt() : 1500;
        Integer icon    = node.hasNonNull("rating") ? node.get("rating").asInt() : 1;

        if (playerId == null) {
            return null;
        }
        return new JwtIdentity(playerId, username, rating, icon);
    }

    public record JwtIdentity(String playerId, String username, Integer rating, Integer icon) {}
}
