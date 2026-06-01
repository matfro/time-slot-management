package pl.mfro.doodle.scheduling.infrastructure.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "meetings")
public class MeetingEntity {

  @Getter
  @Id
  private UUID id;

  @Getter
  @Column(name = "time_slot_id", nullable = false, unique = true)
  private UUID timeSlotId;

  @Getter
  @Column(nullable = false)
  private String title;

  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Getter
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "meeting_participants", joinColumns = @JoinColumn(name = "meeting_id"))
  @Column(name = "participant_email")
  private List<String> participants;

  protected MeetingEntity() {}

  public MeetingEntity(UUID id, UUID timeSlotId, String title, String description, List<String> participants, Instant createdAt) {
    this.id = id;
    this.timeSlotId = timeSlotId;
    this.title = title;
    this.description = description;
    this.participants = participants;
    this.createdAt = createdAt;
  }
}
