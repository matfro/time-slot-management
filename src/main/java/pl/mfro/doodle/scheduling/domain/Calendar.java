package pl.mfro.doodle.scheduling.domain;

import java.util.List;
import java.util.UUID;

public record Calendar(UUID userId, List<TimeSlot> slots) {

  public void addValidatedSlot(TimeSlot newSlot) {
    boolean overlaps = slots.stream().anyMatch(existingSlot -> existingSlot.overlapsWith(newSlot));
    if (overlaps) {
      throw new IllegalArgumentException("New slot overlaps with an existing one");
    }
    this.slots.add(newSlot);
  }
}
