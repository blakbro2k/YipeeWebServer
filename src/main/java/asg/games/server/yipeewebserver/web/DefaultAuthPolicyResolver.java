package asg.games.server.yipeewebserver.web;

import asg.games.server.yipeewebserver.controllers.ControllerConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class DefaultAuthPolicyResolver implements AuthPolicyResolver {

    @Override
    public AuthPolicy resolve(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.startsWith(ControllerConstants.FULL_API_PUBLIC_BASE)) {
            return AuthPolicy.NONE;
        }

        // bootstrap endpoint: requires API token, not game_session
        if (path.equals(ControllerConstants.FULL_API_GAME_LAUNCH_TOKEN_PATH)) {
            return AuthPolicy.API;
        }

        if (path.startsWith(ControllerConstants.FULL_API_GAME_BASE + "/")) {
            return AuthPolicy.GAME_SESSION;
        }

        if (path.startsWith(ControllerConstants.API_BASE_PATH)) {
            return AuthPolicy.API;
        }

        return AuthPolicy.NONE;
    }
}