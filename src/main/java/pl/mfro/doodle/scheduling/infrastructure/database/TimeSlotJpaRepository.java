package pl.mfro.doodle.scheduling.infrastructure.database;

import java.time.ZonedDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TimeSlotJpaRepository extends JpaRepository<TimeSlotEntity, UUID> {

  @Query(value = "SELECT * FROM time_slots " +
          "WHERE user_id = :userId " +
          "AND duration_range && tstzrange(:from, :to, '[)')",
          nativeQuery = true)
  List<TimeSlotEntity> findSlotsInPeriod(
          @Param("userId") UUID userId,
          @Param("from") ZonedDateTime from,
          @Param("to") ZonedDateTime to
  );
}
