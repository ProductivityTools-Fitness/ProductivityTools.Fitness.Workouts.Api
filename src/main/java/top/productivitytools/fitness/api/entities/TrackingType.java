package top.productivitytools.fitness.api.entities;

/**
 * How a set of a given exercise is measured.
 *
 * <p>Drives which columns the UI shows and which fields an import fills in. Mirrors the
 * distinction Hevy makes between a bench press and a plank.
 *
 * <p>Note: {@code Workout.status} is still a plain {@code String}; this is the first enum in
 * the model. It is one here because a wrong value would silently break the set editor, and the
 * database CHECK constraint added in V11 already enforces the same closed set of values.
 */
public enum TrackingType {

    /** Weight x reps. The default and by far the most common. */
    WEIGHT_REPS,

    /** Bodyweight reps, e.g. push-ups. */
    REPS_ONLY,

    /** Time only, e.g. plank, wall sit. */
    DURATION,

    /** Time under an external load, e.g. weighted plank, farmer's walk. */
    DURATION_WEIGHT,

    /** Distance and time, e.g. running, rowing machine. */
    DISTANCE_DURATION;

    /** True when a set of this type is primarily described by a stopwatch. */
    public boolean tracksDuration() {
        return this == DURATION || this == DURATION_WEIGHT || this == DISTANCE_DURATION;
    }

    /** True when a set of this type carries an external load. */
    public boolean tracksWeight() {
        return this == WEIGHT_REPS || this == DURATION_WEIGHT;
    }

    /** True when a set of this type counts repetitions. */
    public boolean tracksReps() {
        return this == WEIGHT_REPS || this == REPS_ONLY;
    }

    /** True when a set of this type records a distance. */
    public boolean tracksDistance() {
        return this == DISTANCE_DURATION;
    }
}
