package cl.duoc.backendiii.mspayment.config;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestControllerAdvice
public class PaymentExceptionHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> serviceError(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",
                ex.getReason() == null ? "Operacion no disponible" : ex.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalidRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
