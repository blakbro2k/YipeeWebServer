package asg.games.server.yipeewebserver.web;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthPolicyResolver {
    AuthPolicy resolve(HttpServletRequest request);
}