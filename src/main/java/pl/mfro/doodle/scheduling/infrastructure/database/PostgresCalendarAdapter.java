package pl.mfro.doodle.scheduling.infrastructure.database;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pl.mfro.doodle.scheduling.domain.CalendarRepository;
import pl.mfro.doodle.scheduling.domain.Meeting;
import pl.mfro.doodle.scheduling.domain.TimeSlot;
import pl.mfro.doodle.scheduling.infrastructure.SchedulingMapper;

@Component
public class PostgresCalendarAdapter implements CalendarRepository {

  private final TimeSlotJpaRepository timeSlotRepository;
  private final MeetingJpaRepository meetingJpaRepository;

  public PostgresCalendarAdapter(
      TimeSlotJpaRepository timeSlotRepository, MeetingJpaRepository meetingJpaRepository) {
    this.timeSlotRepository = timeSlotRepository;
    this.meetingJpaRepository = meetingJpaRepository;
  }

  @Override
  public void save(TimeSlot timeSlot) {
    timeSlotRepository
        .findById(timeSlot.getId())
        .ifPresentOrElse(
            existingEntity -> {
              existingEntity.setFree(timeSlot.isFree());
              existingEntity.setDurationRangeFromInstants(
                  timeSlot.getStartTime(), timeSlot.getEndTime());
              timeSlotRepository.save(existingEntity);
            },
            () -> {
              TimeSlotEntity newEntity = SchedulingMapper.toEntity(timeSlot);
              timeSlotRepository.save(newEntity);
            });
  }

  @Override
  public Optional<TimeSlot> findById(UUID slotId) {
    return timeSlotRepository.findById(slotId).map(TimeSlotEntity::toDomain);
  }

  @Override
  public void delete(UUID slotId) {
    timeSlotRepository.deleteById(slotId);
  }

  @Override
  public void saveMeeting(Meeting meeting) {
    MeetingEntity entity =
        new MeetingEntity(
            meeting.id(),
            meeting.timeSlotId(),
            meeting.title(),
            meeting.description(),
            meeting.participants(),
            meeting.createdAt());

    meetingJpaRepository.save(entity);
  }

  @Override
  public List<TimeSlot> findSlotsInPeriod(UUID userId, ZonedDateTime from, ZonedDateTime to) {
    return timeSlotRepository.findSlotsInPeriod(userId, from, to).stream()
        .map(TimeSlotEntity::toDomain)
        .collect(Collectors.toList());
  }
}
