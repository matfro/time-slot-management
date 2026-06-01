package pl.mfro.doodle.scheduling.infrastructure.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String message,
        String code,
        Instant timestamp,
        List<String> details
) {
  public static ErrorResponse of(String message, String code) {
    return new ErrorResponse(message, code, Instant.now(), List.of());
  }

  public static ErrorResponse of(String message, String code, List<String> details) {
    return new ErrorResponse(message, code, Instant.now(), details);
  }
}
