package pl.mfro.doodle.scheduling.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public class TimeSlot {
  private final UUID id;
  private final UUID userId;
  private Instant startTime;
  private Instant endTime;
  private boolean isFree;
  private Long version;

  public TimeSlot(UUID id, UUID userId, Instant startTime, Instant endTime, boolean isFree) {
    if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
      throw new IllegalArgumentException("End time must be after the start time");
    }
    this.id = id;
    this.userId = userId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.isFree = isFree;
  }

  public TimeSlot(UUID id, UUID userId, Instant startTime, Instant endTime, boolean isFree, Long version) {
    this(id, userId, startTime, endTime, isFree);
    this.version = version;
  }

  public boolean overlapsWith(TimeSlot other) {
    return this.startTime.isBefore(other.getEndTime()) && other.getStartTime().isBefore(this.endTime);
  }

  public void updatePeriod(Instant newStart, Instant newEnd) {
    if (newStart.isAfter(newEnd) || newStart.equals(newEnd)) {
      throw new IllegalArgumentException("End time must be after the start time");
    }
    this.startTime = newStart;
    this.endTime = newEnd;
  }

  public void markAsFree() { this.isFree = true; }
  public void markAsBusy() { this.isFree = false; }

  public void convertToMeeting() {
    if (!this.isFree) {
      throw new IllegalStateException("This slot has already been reserved");
    }
    this.isFree = false;
  }

}

