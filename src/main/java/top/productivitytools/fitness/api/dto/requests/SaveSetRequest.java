package top.productivitytools.fitness.api.dto.requests;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

/**
 * Partial update of a single set: every field is optional and only the ones present are
 * applied, so the UI can save one cell at a time.
 */
public record SaveSetRequest(
    @JsonAlias({"id", "setId"})
    Long id,

    @JsonAlias({"kg", "weightKg", "weight"})
    BigDecimal kg,

    Integer reps,

    /** Time under tension in seconds, for exercises held rather than repeated. */
    @JsonAlias({"durationSeconds", "duration"})
    Integer durationSeconds,

    /** Distance in metres, for cardio. */
    @JsonAlias({"distanceMeters", "distance"})
    BigDecimal distanceMeters,

    @JsonAlias({"status", "isCompleted", "completed"})
    Boolean status
) {}
