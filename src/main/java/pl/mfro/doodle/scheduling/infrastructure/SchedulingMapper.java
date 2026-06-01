package pl.mfro.doodle.scheduling.infrastructure;

import java.time.ZoneOffset;
import java.util.UUID;
import pl.mfro.doodle.scheduling.domain.Meeting;
import pl.mfro.doodle.scheduling.domain.TimeSlot;
import pl.mfro.doodle.scheduling.infrastructure.database.TimeSlotEntity;
import pl.mfro.doodle.scheduling.infrastructure.web.MeetingResponse;
import pl.mfro.doodle.scheduling.infrastructure.web.SlotCreationRequest;
import pl.mfro.doodle.scheduling.infrastructure.web.TimeSlotResponse;

public final class SchedulingMapper {

  private SchedulingMapper() {}

  public static TimeSlot toDomain(SlotCreationRequest request, UUID userId) {
    return new TimeSlot(UUID.randomUUID(), userId, request.startTime(), request.endTime(), true);
  }

  public static TimeSlotEntity toEntity(TimeSlot domain) {

    return new TimeSlotEntity(
        domain.getId(),
        domain.getUserId(),
        domain.getStartTime().atZone(ZoneOffset.UTC),
        domain.getEndTime().atZone(ZoneOffset.UTC),
        domain.isFree(),
        domain.getVersion());
  }

  public static TimeSlotResponse toResponse(TimeSlot domain) {
    return new TimeSlotResponse(
        domain.getId(),
        domain.getUserId(),
        domain.getStartTime(),
        domain.getEndTime(),
        domain.isFree());
  }

  public static MeetingResponse toResponse(Meeting domain) {
    return new MeetingResponse(
        domain.id(),
        domain.timeSlotId(),
        domain.title(),
        domain.description(),
        domain.participants(),
        domain.createdAt());
  }
}
