package top.productivitytools.fitness.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "workout_set")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_exercise_id", nullable = false)
    @JsonIgnore
    private WorkoutExercise workoutExercise;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer reps = 0;

    /**
     * Time under tension, for exercises held rather than repeated (plank, wall sit).
     * Null for a plain weight x reps set.
     */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Distance covered, for cardio (running, rowing machine). Null otherwise. */
    @Column(name = "distance_meters", precision = 10, scale = 2)
    private BigDecimal distanceMeters;

    @Transient
    private BigDecimal prevWeightKg;

    @Transient
    private Integer prevReps;

    @Transient
    private Integer prevDurationSeconds;

    @Transient
    private BigDecimal prevDistanceMeters;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
