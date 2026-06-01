package pl.mfro.doodle.scheduling.infrastructure.database;

import jakarta.persistence.*;
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType;
import io.hypersistence.utils.hibernate.type.range.Range;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import pl.mfro.doodle.scheduling.domain.TimeSlot;

@Entity
@Table(name = "time_slots")
public class TimeSlotEntity {

  @Getter
  @Id
  private UUID id;

  @Getter
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Setter
  @Getter
  @Column(name = "is_free", nullable = false)
  private boolean isFree;

  @Getter
  @Version
  private Long version;

  @Type(PostgreSQLRangeType.class)
  @Column(name = "duration_range", columnDefinition = "tstzrange", nullable = false)
  private Range<ZonedDateTime> durationRange;

  protected TimeSlotEntity() {}

  public TimeSlotEntity(UUID id, UUID userId, ZonedDateTime startTime, ZonedDateTime endTime, boolean isFree, Long version) {
    this(id, userId, startTime, endTime, isFree);
    this.version = version;
  }

  public TimeSlotEntity(UUID id, UUID userId, ZonedDateTime startTime, ZonedDateTime endTime, boolean isFree) {
    this.id = id;
    this.userId = userId;
    this.isFree = isFree;
    this.durationRange = Range.closedOpen(startTime, endTime);
  }

  public void setDurationRangeFromInstants(Instant startTime, Instant endTime) {
    this.durationRange = Range.closedOpen(startTime.atZone(ZoneOffset.UTC), endTime.atZone(ZoneOffset.UTC));
  }

  public TimeSlot toDomain() {
    return new TimeSlot(id, userId, durationRange.lower().toInstant(), durationRange.upper().toInstant(), isFree, this.version);
  }

  @Transient public ZonedDateTime getStartTime() { return durationRange.lower(); }
  @Transient public ZonedDateTime getEndTime() { return durationRange.upper(); }
}
