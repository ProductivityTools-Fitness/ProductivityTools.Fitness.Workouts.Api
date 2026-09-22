package top.productivitytools.fitness.api.services.hevy;

import java.util.List;

/**
 * One row of the Hevy name-mapping catalogue: how a title exported from Hevy maps onto an
 * exercise in this system.
 *
 * @param catalogExerciseId business key in Fitness.Catalog.Api, or null when this exercise has
 *                          no counterpart in the catalogue and lives only locally
 */
public record HevyExerciseCatalogItem(
        String hevyTitle,
        String hevyPlTitle,
        String name,
        String catalogExerciseId,
        String equipmentCategory,
        String bodyCategory,
        String targetMuscle,
        List<String> secondaryMuscles,
        List<String> instructions,
        String gifUrl
) {}
