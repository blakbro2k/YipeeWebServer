package asg.games.server.yipeewebserver.web;

import asg.games.server.yipeewebserver.annotations.SessionConnection;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.exceptions.ClientValidationException;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.security.DevJwtAuthenticationFilter;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.yipee.core.objects.YipeePlayer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionConnectionArgumentResolver implements HandlerMethodArgumentResolver {
    public static final String HEADER_CLIENT_ID  = "X-Client-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    private final SessionService sessionService;
    private final YipeePlayerRepository yipeePlayerRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(SessionConnection.class)
                && PlayerConnectionEntity.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NotNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        log.debug("Enter resolveArgument(parameter={}, mavContainer={}, webRequest={}, binderFactory={})", parameter, mavContainer, webRequest, binderFactory);
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest(HttpServletRequest.class);
        assert request != null;

        String clientId  = request.getHeader(HEADER_CLIENT_ID);
        String sessionId = request.getHeader(HEADER_SESSION_ID);

        log.debug("clientId={}",clientId );
        log.debug("sessionId={}", sessionId);
        // Preferred: header-based session (existing behavior)
        if (sessionId != null && !sessionId.isBlank() && clientId != null && !clientId.isBlank()) {
            return sessionService.requireSession(sessionId, clientId);
        }

        // Fallback: JWT-authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.debug("auth={}", auth);
        if (auth != null && auth.isAuthenticated()) {
            String playerId = null;

            Object details = auth.getDetails();
            if (details instanceof DevJwtAuthenticationFilter.JwtIdentity ji) {
                playerId = ji.playerId();
            } else if (auth.getPrincipal() instanceof String s && !s.isBlank()) {
                playerId = s; // matches your dev filter logs
            } else if (auth.getPrincipal() instanceof DevJwtAuthenticationFilter.JwtIdentity ji2) {
                playerId = ji2.playerId();
            }

            if (playerId != null) {
                log.debug("playerId={}", playerId);
                // Create or load a PlayerConnectionEntity for this player.
                // Pick the method you already have (examples):
                //return sessionService.requireSession(sessionId, clientId);
                // or sessionService.getOrCreateConnection(playerId);
                YipeePlayer player = yipeePlayerRepository.findById(playerId).orElse(null);
                log.debug("Exit resolveArgument()={}", player);
                return player;
            }
        }

        // No headers + no JWT identity
        throw new ClientValidationException(ClientValidationException.PLAYER_NOT_FOUND, "Missing/invalid session or bearer token");
    }
}
