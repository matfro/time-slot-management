package pl.mfro.doodle.scheduling.domain;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarRepository {

  void save(TimeSlot timeSlot);

  Optional<TimeSlot> findById(UUID slotId);

  void delete(UUID slotId);

  List<TimeSlot> findSlotsInPeriod(UUID userId, ZonedDateTime from, ZonedDateTime to);

  void saveMeeting(Meeting meeting);
}
