package asg.games.server.yipeewebserver.exceptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientValidationException extends RuntimeException {
    public static final String CLIENT_ID_MISSING = "CLIENT_ID_MISSING";
    public static final String PLAYER_NOT_FOUND = "PLAYER_NOT_FOUND";
    private final String code;

    public ClientValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}