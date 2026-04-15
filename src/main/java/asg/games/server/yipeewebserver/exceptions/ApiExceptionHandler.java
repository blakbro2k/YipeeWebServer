package asg.games.server.yipeewebserver.exceptions;

import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ClientValidationException.class)
    public ResponseEntity<ErrorResponse> handleClientError(ClientValidationException ex) {
        ErrorResponse body = new ErrorResponse(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Log full detail server-side
        log.warn("Data integrity violation", ex);

        ex.getMostSpecificCause();
        String mostSpecific = ex.getMostSpecificCause().getMessage();

        // Your H2 constraint/index name shows up in the error:
        // "PUBLIC.UK_YTPC_PLAYER_CLIENT_INDEX_6 ..."
        if (mostSpecific != null && mostSpecific.contains("UK_YTPC_PLAYER_CLIENT")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "PLAYER_ALREADY_REGISTERED",
                            "You’re already registered in this browser. Click 'Who Am I' or clear local storage to re-register."
                    ));
        }

        // Generic fallback
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "DATA_CONSTRAINT_VIOLATION",
                        "That request conflicts with existing data."
                ));
    }

    // Optional: make unknown exceptions return friendly 500 without stacktrace
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SERVER_ERROR", "Something went wrong. Check server logs."));
    }

    @ExceptionHandler(ApiObjectMissingException.class)
    public ResponseEntity<ErrorResponse> handleMissingObject(ApiObjectMissingException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_OBJECT", "Invalid or expired Object Id."));
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ErrorResponse> handleJwt(SignatureException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_TOKEN", "Invalid or expired token."));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwt(JwtException ex) {
        log.warn("JWT error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_TOKEN", "Your token is invalid or expired. Please re-login / re-launch the game."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", ex.getMessage()));
    }

    public record ErrorResponse(String code, String message) {}
}
