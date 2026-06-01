package pl.mfro.doodle.scheduling.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateSlotRequest(
        @NotNull(message = "Start time is required") Instant startTime,
        @NotNull(message = "End time is required") Instant endTime,
        boolean isFree
) {}
