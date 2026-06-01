package pl.mfro.doodle.scheduling.infrastructure.web;

import java.time.Instant;
import java.util.UUID;

public record TimeSlotResponse(
        UUID id,
        UUID userId,
        Instant startTime,
        Instant endTime,
        boolean isFree
) {}
