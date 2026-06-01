package pl.mfro.doodle.scheduling.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Meeting(UUID id, UUID timeSlotId, String title, String description, List<String> participants, Instant createdAt) {
  public Meeting {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title must not be empty");
    }
    if (participants == null || participants.isEmpty()) {
      throw new IllegalArgumentException("Meeting must have at least one participant");
    }
  }
}
