package top.productivitytools.fitness.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import top.productivitytools.fitness.api.dto.responses.WorkoutSummaryDto;
import top.productivitytools.fitness.api.entities.Workout;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRepository extends JpaRepository<Workout, Long> {
    List<Workout> findAllByOrderByStartTimeDesc();
    List<Workout> findByUserIdOrderByStartTimeDesc(Long userId);

    @Query("""
        SELECT new top.productivitytools.fitness.api.dto.responses.WorkoutSummaryDto(
            w.id,
            w.workoutNumber,
            w.user.id,
            w.title,
            w.startTime,
            w.endTime,
            w.durationSeconds,
            w.status,
            w.notes,
            w.createdAt,
            w.updatedAt
        )
        FROM Workout w
        WHERE w.user.id = :userId
        ORDER BY w.startTime DESC
    """)
    List<WorkoutSummaryDto> findSummariesByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(MAX(w.workoutNumber), 0) FROM Workout w WHERE w.user.id = :userId")
    int findMaxWorkoutNumberByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndStartTime(Long userId, OffsetDateTime startTime);

    Optional<Workout> findByUserIdAndStartTime(Long userId, OffsetDateTime startTime);
}
