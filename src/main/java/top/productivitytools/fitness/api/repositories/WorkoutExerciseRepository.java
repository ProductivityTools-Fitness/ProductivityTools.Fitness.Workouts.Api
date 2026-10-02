package top.productivitytools.fitness.api.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import top.productivitytools.fitness.api.entities.WorkoutExercise;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface WorkoutExerciseRepository extends JpaRepository<WorkoutExercise, Long> {
    List<WorkoutExercise> findByWorkoutIdOrderByOrderIndexAsc(Long workoutId);

    @Query("""
        SELECT we FROM WorkoutExercise we
        JOIN we.workout w
        WHERE w.user.id = :userId
          AND w.id != :currentWorkoutId
          AND we.exercise.id = :exerciseId
          AND (w.startTime < :currentWorkoutStartTime OR (w.startTime = :currentWorkoutStartTime AND w.id < :currentWorkoutId))
          AND we.sets IS NOT EMPTY
        ORDER BY w.startTime DESC, w.id DESC
    """)
    List<WorkoutExercise> findPastExercises(
        @Param("userId") Long userId,
        @Param("exerciseId") Long exerciseId,
        @Param("currentWorkoutId") Long currentWorkoutId,
        @Param("currentWorkoutStartTime") OffsetDateTime currentWorkoutStartTime,
        Pageable pageable
    );

    /** Every workout in which the user performed the exercise and logged at least one set, newest first. */
    @Query("""
        SELECT we FROM WorkoutExercise we
        JOIN FETCH we.workout w
        WHERE w.user.id = :userId
          AND we.exercise.id = :exerciseId
          AND we.sets IS NOT EMPTY
        ORDER BY w.startTime DESC, w.id DESC
    """)
    List<WorkoutExercise> findHistoryForExercise(
        @Param("userId") Long userId,
        @Param("exerciseId") Long exerciseId,
        Pageable pageable
    );
}

