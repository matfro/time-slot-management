package pl.mfro.doodle.scheduling.infrastructure.web;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleDomainValidation(IllegalArgumentException ex) {
    ErrorResponse error = ErrorResponse.of(ex.getMessage(), "INVALID_DOMAIN_RESOURCE");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleConcurrencyConflict(IllegalStateException ex) {
    ErrorResponse error = ErrorResponse.of(ex.getMessage(), "CONCURRENCY_CONFLICT");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ErrorResponse> handleTenantViolation(SecurityException ex) {
    ErrorResponse error = ErrorResponse.of(ex.getMessage(), "TENANT_ACCESS_DENIED");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleRequestValidation(MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());

    ErrorResponse error = ErrorResponse.of("Validation error", "INVALID_REQUEST_PAYLOAD", details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error(ex.getMessage(), ex);
    ErrorResponse error =
        ErrorResponse.of("Unexpected server error. Try again later.", "INTERNAL_SERVER_ERROR");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
