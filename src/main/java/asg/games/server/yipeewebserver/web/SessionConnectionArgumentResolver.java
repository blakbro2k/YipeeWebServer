package asg.games.server.yipeewebserver.web;

import asg.games.server.yipeewebserver.annotations.SessionConnection;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.services.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
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
            HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest(HttpServletRequest.class);

            assert request != null;
            String clientId  = request.getHeader(HEADER_CLIENT_ID);
            String sessionId = request.getHeader(HEADER_SESSION_ID);

            // This will throw your ClientValidationException if invalid/expired
            return sessionService.requireSession(sessionId, clientId);
        }
}
