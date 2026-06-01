package pl.mfro.doodle.scheduling.infrastructure.web;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.mfro.doodle.scheduling.application.SchedulingService;
import pl.mfro.doodle.scheduling.domain.Meeting;
import pl.mfro.doodle.scheduling.domain.TimeSlot;
import pl.mfro.doodle.scheduling.infrastructure.SchedulingMapper;

@RestController
@Timed
@RequestMapping("/api/v1/slots")
public class TimeSlotController {

  private final SchedulingService schedulingService;

  public TimeSlotController(SchedulingService schedulingService) {
    this.schedulingService = schedulingService;
  }

  @PostMapping
  public ResponseEntity<TimeSlotResponse> createSlot(
      @RequestHeader(value = "X-User-Id") UUID userId,
      @Valid @RequestBody SlotCreationRequest request) {
    TimeSlot domainModel = SchedulingMapper.toDomain(request, userId);
    TimeSlot created = schedulingService.createTimeSlot(userId, domainModel);
    return ResponseEntity.status(HttpStatus.CREATED).body(SchedulingMapper.toResponse(created));
  }

  @PutMapping("/{id}")
  public ResponseEntity<TimeSlotResponse> updateSlot(
      @RequestHeader(value = "X-User-Id") UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSlotRequest request) {
    TimeSlot updated =
        schedulingService.updateTimeSlot(
            userId, id, request.startTime(), request.endTime(), request.isFree());
    return ResponseEntity.ok(SchedulingMapper.toResponse(updated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteSlot(
      @RequestHeader(value = "X-User-Id") UUID userId, @PathVariable UUID id) {
    schedulingService.deleteTimeSlot(userId, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/book")
  public ResponseEntity<MeetingResponse> bookMeeting(
      @RequestHeader(value = "X-User-Id") UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody ConvertSlotToMeetingRequest request) {
    final Meeting meeting =
        schedulingService.scheduleMeetingWithLock(
            userId, id, request.title(), request.description(), request.participants());
    return ResponseEntity.status(HttpStatus.CREATED).body(SchedulingMapper.toResponse(meeting));
  }

  @GetMapping
  public ResponseEntity<List<TimeSlotResponse>> getSlots(
      @RequestHeader(value = "X-User-Id") UUID userId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    List<TimeSlot> availableSlots = schedulingService.getAvailableSlots(userId, from, to);
    List<TimeSlotResponse> response =
        availableSlots.stream().map(SchedulingMapper::toResponse).collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }
}
