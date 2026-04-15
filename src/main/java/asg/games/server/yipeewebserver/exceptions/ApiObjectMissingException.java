package asg.games.server.yipeewebserver.exceptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiObjectMissingException extends RuntimeException {
    public ApiObjectMissingException(String message) {
        super(message);
    }
}