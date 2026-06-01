package pl.mfro.doodle.scheduling.infrastructure.database;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeetingJpaRepository extends JpaRepository<MeetingEntity, UUID> {
}
