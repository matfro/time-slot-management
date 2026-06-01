package pl.mfro.doodle.scheduling.infrastructure.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(
    UUID id,
    UUID timeSlotId,
    String title,
    String description,
    List<String> participants,
    Instant createdAt) {}
