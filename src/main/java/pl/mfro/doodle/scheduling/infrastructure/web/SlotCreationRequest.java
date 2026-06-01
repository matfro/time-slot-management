package pl.mfro.doodle.scheduling.infrastructure.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SlotCreationRequest(

        @NotNull(message = "Start time must not be null")
        @Future(message = "Start time must be in the future")
        Instant startTime,

        @NotNull(message = "End time must not be null")
        @Future(message = "End time must be in the future")
        Instant endTime
) {


  @AssertTrue(message = "Start time must be before the end time")
  private boolean isChronological() {
    if (startTime == null || endTime == null) {
      return true;
    }
    return startTime.isBefore(endTime);
  }
}
