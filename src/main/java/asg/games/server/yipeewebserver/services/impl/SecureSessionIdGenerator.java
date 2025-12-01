package asg.games.server.yipeewebserver.services.impl;

import asg.games.server.yipeewebserver.services.SessionIdGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureSessionIdGenerator implements SessionIdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateSessionId() {
        byte[] bytes = new byte[16]; // 128 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}