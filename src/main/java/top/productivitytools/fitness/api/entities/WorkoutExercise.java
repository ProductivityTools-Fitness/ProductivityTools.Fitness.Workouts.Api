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

    public WorkoutSet addSet() {
        WorkoutSet previousSet = sets.isEmpty() ? null : sets.get(sets.size() - 1);
        BigDecimal defaultWeight = previousSet != null ? previousSet.getWeightKg() : BigDecimal.ZERO;
        Integer defaultReps = previousSet != null ? previousSet.getReps() : 0;
        return addSet(defaultWeight, defaultReps);
    }

    public WorkoutSet addSet(BigDecimal weightKg, Integer reps) {
        return addSet(weightKg, reps, null, null);
    }

    public WorkoutSet addSet(BigDecimal weightKg, Integer reps, BigDecimal prevWeightKg, Integer prevReps) {
        WorkoutSet newSet = new WorkoutSet();
        newSet.setWorkoutExercise(this);
        newSet.setSetNumber(sets.size() + 1);
        newSet.setWeightKg(weightKg != null ? weightKg : BigDecimal.ZERO);
        newSet.setReps(reps != null ? reps : 0);
        newSet.setPrevWeightKg(prevWeightKg);
        newSet.setPrevReps(prevReps);
        newSet.setIsCompleted(false);
        this.sets.add(newSet);
        return newSet;
    }
}
