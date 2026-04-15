package asg.games.server.yipeewebserver.security;

import asg.games.server.yipeewebserver.web.AuthPolicy;
import asg.games.server.yipeewebserver.web.AuthPolicyResolver;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import asg.games.server.yipeewebserver.services.LaunchTokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import asg.games.server.yipeewebserver.services.GameSessionTokenService;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_SCOPE_ROLE = "ROLE_API";
    public static final String GAME_SESSION_SCOPE_ROLE = "ROLE_GAME_SESSION";

    private final LaunchTokenService launchTokenService;
    private final GameSessionTokenService gameSessionTokenService;
    private final AuthPolicyResolver authPolicyResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        AuthPolicy policy = authPolicyResolver.resolve(req);

        if (policy == AuthPolicy.NONE) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            unauthorized(res, "missing_token", "Authorization Bearer token required");
            return;
        }

        String token = auth.substring("Bearer ".length()).trim();

        try {
            Claims claims = switch (policy) {
                case API -> launchTokenService.verifyAPIToken(token).getBody();
                case GAME_SESSION -> gameSessionTokenService.verifyGameSessionToken(token).getBody();
                default -> throw new IllegalStateException("Unsupported auth policy: " + policy);
            };

            String playerId = claims.getSubject();
            String scope = claims.get("scope", String.class);
            String requiredScope = requiredScope(policy);

            if (playerId == null || playerId.isBlank()) {
                unauthorized(res, "invalid_token", "Missing subject");
                return;
            }

            if (!requiredScope.equals(scope)) {
                unauthorized(res, "insufficient_scope", requiredScope + " token required");
                return;
            }

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority(requiredRole(policy))
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(playerId, "[PROTECTED]", authorities);
            authentication.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(req, res);

        } catch (IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            unauthorized(res, "invalid_token", "JWT invalid or expired");
        }
    }

    private String requiredScope(AuthPolicy policy) {
        return switch (policy) {
            case API -> "api";
            case GAME_SESSION -> "game_session";
            case LAUNCH -> "launch";
            default -> "";
        };
    }

    private String requiredRole(AuthPolicy policy) {
        return switch (policy) {
            case API -> API_SCOPE_ROLE;
            case GAME_SESSION -> GAME_SESSION_SCOPE_ROLE;
            case LAUNCH -> "ROLE_LAUNCH";
            default -> "ROLE_NONE";
        };
    }

    private void unauthorized(HttpServletResponse res, String error, String description) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("""
            {"error":"%s","error_description":"%s"}
            """.formatted(error, description));
    }
}