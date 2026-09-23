package top.productivitytools.fitness.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_exercise")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id", nullable = false)
    @JsonIgnore
    private Workout workout;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "rest_timer_seconds")
    private Integer restTimerSeconds;

    @OneToMany(mappedBy = "workoutExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber ASC")
    private List<WorkoutSet> sets = new ArrayList<>();

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /**
     * Adds a set shaped like the last one of this exercise. Copying the previous values
     * matters for timed exercises: a second plank set should start from the first one's
     * duration, not from an empty weight x reps row.
     */
    public WorkoutSet addSet() {
        WorkoutSet previousSet = sets.isEmpty() ? null : sets.get(sets.size() - 1);
        WorkoutSet newSet = addSet(
                previousSet != null ? previousSet.getWeightKg() : BigDecimal.ZERO,
                previousSet != null ? previousSet.getReps() : 0);
        if (previousSet != null) {
            newSet.setDurationSeconds(previousSet.getDurationSeconds());
            newSet.setDistanceMeters(previousSet.getDistanceMeters());
        }
        return newSet;
    }

    public WorkoutSet addSet(BigDecimal weightKg, Integer reps) {
        WorkoutSet newSet = new WorkoutSet();
        newSet.setWorkoutExercise(this);
        newSet.setSetNumber(sets.size() + 1);
        newSet.setWeightKg(weightKg != null ? weightKg : BigDecimal.ZERO);
        newSet.setReps(reps != null ? reps : 0);
        newSet.setIsCompleted(false);
        this.sets.add(newSet);
        return newSet;
    }

    /**
     * Adds a set pre-filled from the same set number of a past workout, and records those
     * past values as the "previous" hints shown next to the inputs.
     */
    public WorkoutSet addSetFrom(WorkoutSet pastSet) {
        WorkoutSet newSet = addSet(pastSet.getWeightKg(), pastSet.getReps());
        newSet.setDurationSeconds(pastSet.getDurationSeconds());
        newSet.setDistanceMeters(pastSet.getDistanceMeters());
        newSet.setPrevWeightKg(pastSet.getWeightKg());
        newSet.setPrevReps(pastSet.getReps());
        newSet.setPrevDurationSeconds(pastSet.getDurationSeconds());
        newSet.setPrevDistanceMeters(pastSet.getDistanceMeters());
        return newSet;
    }
}
