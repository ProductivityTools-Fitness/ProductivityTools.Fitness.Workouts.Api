package top.productivitytools.fitness.api.dto.requests;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DeleteExerciseRequest(
    @JsonAlias({"id", "workoutExerciseId"})
    Long workoutExerciseId
) {}
