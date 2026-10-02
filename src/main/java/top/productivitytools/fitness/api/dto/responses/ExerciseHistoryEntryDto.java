package top.productivitytools.fitness.api.dto.responses;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One past workout in which an exercise was performed, with all its sets. Used by the exercise
 * detail page to draw the progress chart.
 */
public record ExerciseHistoryEntryDto(
        Long workoutId,
        Integer workoutNumber,
        String workoutTitle,
        OffsetDateTime startTime,
        String status,
        List<ExerciseHistorySetDto> sets
) {
    public record ExerciseHistorySetDto(
            Integer setNumber,
            BigDecimal weightKg,
            Integer reps,
            Integer durationSeconds,
            BigDecimal distanceMeters,
            Boolean isCompleted
    ) {}
}
