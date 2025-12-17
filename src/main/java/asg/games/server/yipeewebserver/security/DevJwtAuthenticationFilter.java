package asg.games.server.yipeewebserver.security;

import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
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
@Component
@RequiredArgsConstructor
public class DevJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YipeePlayerRepository yipeePlayerRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            log.debug("Token starts: {}", token.substring(0, Math.min(20, token.length())));
            try {
                JwtIdentity identity = parseJwt(token);
                if (identity != null && identity.playerId() != null) {

                    boolean exists = yipeePlayerRepository.existsById(identity.playerId());
                    if (!exists) {
                        log.warn("Dev JWT sub not found in DB: {}", identity.playerId());
                        // don’t set auth; let downstream return 401/403
                    } else {
                        var auth = new UsernamePasswordAuthenticationToken(
                                identity.playerId(),
                                null,
                                Collections.emptyList()
                        );
                        auth.setDetails(identity);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.debug("After DevJwtAuthenticationFilter auth={}",auth);
                    }
                }
            } catch (Exception e) {
                log.warn("DevJwtAuthenticationFilter: failed to parse token: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private JwtIdentity parseJwt(String token) throws IOException {
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;

        String payloadBase64 = padBase64Url(parts[1]);
        byte[] decoded = Base64.getUrlDecoder().decode(payloadBase64);
        String json = new String(decoded, StandardCharsets.UTF_8);

        JsonNode node = objectMapper.readTree(json);

        String playerId = node.path("sub").asText(null);
        String username = node.path("username").asText(null);
        Integer rating  = node.hasNonNull("rating") ? node.get("rating").asInt() : 1500;

        // icon can be int or string; treat as string to be safe
        String icon = node.path("icon").asText(null);

        if (playerId == null) return null;
        return new JwtIdentity(playerId, username, rating, icon);
    }

    private static String padBase64Url(String s) {
        int mod = s.length() % 4;
        if (mod == 0) return s;
        return s + "====".substring(mod);
    }

    public record JwtIdentity(String playerId, String username, Integer rating, String icon) {}
}
