package pl.mfro.doodle.scheduling.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.mfro.doodle.scheduling.domain.*;

@Service
public class SchedulingService {

  private final CalendarRepository calendarRepository;
  private final RedissonClient redisson;
  private final TransactionTemplate transactionTemplate;

  public SchedulingService(
      CalendarRepository calendarRepository,
      RedissonClient redisson,
      TransactionTemplate transactionTemplate) {
    this.calendarRepository = calendarRepository;
    this.redisson = redisson;
    this.transactionTemplate = transactionTemplate;
  }

  @Transactional
  public TimeSlot createTimeSlot(UUID userId, TimeSlot newSlot) {
    validateOverlap(userId, newSlot.getId(), newSlot.getStartTime(), newSlot.getEndTime());
    calendarRepository.save(newSlot);
    return newSlot;
  }

  @Transactional
  public TimeSlot updateTimeSlot(
      UUID userId, UUID slotId, Instant newStart, Instant newEnd, boolean isFree) {
    TimeSlot slot =
        calendarRepository
            .findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Slot does not exist"));

    if (!slot.getUserId().equals(userId)) {
      throw new SecurityException("You're not authorized to modify this slot");
    }

    validateOverlap(userId, slotId, newStart, newEnd);

    slot.updatePeriod(newStart, newEnd);
    if (isFree) slot.markAsFree();
    else slot.markAsBusy();

    calendarRepository.save(slot);
    return slot;
  }

  @Transactional
  public void deleteTimeSlot(UUID userId, UUID slotId) {
    TimeSlot slot =
        calendarRepository
            .findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Slot does not exist"));

    if (!slot.getUserId().equals(userId)) {
      throw new SecurityException("You're not authorized to remove this slot");
    }
    calendarRepository.delete(slotId);
  }

  public Meeting scheduleMeetingWithLock(
      UUID userId, UUID slotId, String title, String description, List<String> participants) {
    RLock lock = redisson.getLock("schedulemeeting:slot:" + slotId);

    try {
      boolean isLockAcquired = lock.tryLock(1000, 5000, TimeUnit.SECONDS);

      if (!isLockAcquired) {
        throw new IllegalStateException(
            "Could not acquire lock, slotId=" + slotId + " is being modified by another thread");
      }

      return transactionTemplate.execute(
          _ ->
              this.scheduleMeeting(
                  userId, slotId, title, description, participants)); //needed to handle proxy

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for lock");
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  @Transactional
  public Meeting scheduleMeeting(
      UUID userId, UUID slotId, String title, String description, List<String> participants) {
    TimeSlot slot =
        calendarRepository
            .findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Slot does not exist"));

    slot.convertToMeeting();
    calendarRepository.save(slot);

    Meeting meeting =
        new Meeting(UUID.randomUUID(), slotId, title, description, participants, Instant.now());
    calendarRepository.saveMeeting(meeting);
    return meeting;
  }

  @Transactional(readOnly = true)
  public List<TimeSlot> getAvailableSlots(UUID userId, Instant from, Instant to) {
    return calendarRepository.findSlotsInPeriod(
        userId, from.atZone(ZoneOffset.UTC), to.atZone(ZoneOffset.UTC));
  }

  private void validateOverlap(UUID userId, UUID slotId, Instant start, Instant end) {
    List<TimeSlot> conflicting =
        calendarRepository.findSlotsInPeriod(
            userId, start.atZone(ZoneOffset.UTC), end.atZone(ZoneOffset.UTC));
    boolean hasOverlap = conflicting.stream().anyMatch(s -> !s.getId().equals(slotId));
    if (hasOverlap) {
      throw new IllegalArgumentException("New time range overlaps with an existing slot");
    }
  }
}
