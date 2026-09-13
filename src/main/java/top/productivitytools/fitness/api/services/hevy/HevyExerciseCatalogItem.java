package top.productivitytools.fitness.api.services.hevy;

import java.util.List;

public record HevyExerciseCatalogItem(
        String hevyTitle,
        String hevyPlTitle,
        String name,
        String externalExerciseId,
        String equipmentCategory,
        String bodyCategory,
        String targetMuscle,
        List<String> secondaryMuscles,
        List<String> instructions,
        String gifUrl
) {}
