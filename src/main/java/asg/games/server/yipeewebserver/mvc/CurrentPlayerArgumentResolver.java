package asg.games.server.yipeewebserver.mvc;

import asg.games.server.yipeewebserver.annotations.CurrentPlayer;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.exceptions.ClientValidationException;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.yipee.core.objects.YipeePlayer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@RequiredArgsConstructor
public class CurrentPlayerArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_ARG_CLIENT_ID = "X-Client-Id";

    private final YipeeClientConnectionRepository clientConnectionRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentPlayer.class)
                && YipeePlayer.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String clientId = request != null ? request.getHeader(HEADER_ARG_CLIENT_ID) : null;

        if (!StringUtils.hasText(clientId)) {
            throw new ClientValidationException(
                    ClientValidationException.CLIENT_ID_MISSING,
                    HEADER_ARG_CLIENT_ID + " header is required."
            );
        }

        PlayerConnectionEntity playerEntity = clientConnectionRepository.findPlayerByClientId(clientId);

        if (playerEntity == null) {
            throw new ClientValidationException(
                    ClientValidationException.PLAYER_NOT_FOUND,
                    "No player is registered for this client. Please register first."
            );
        }

        return playerEntity.getPlayer(); // YipeePlayer
    }
}