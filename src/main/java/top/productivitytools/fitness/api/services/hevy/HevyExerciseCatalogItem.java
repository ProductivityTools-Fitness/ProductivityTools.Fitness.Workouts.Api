package top.productivitytools.fitness.api.services.hevy;

import top.productivitytools.fitness.api.entities.TrackingType;

import java.util.List;

/**
 * One row of the Hevy name-mapping catalogue: how a title exported from Hevy maps onto an
 * exercise in this system.
 *
 * @param catalogExerciseId business key in Fitness.Catalog.Api, or null when this exercise has
 *                          no counterpart in the catalogue and lives only locally
 * @param trackingType      how a set of this exercise is measured; absent in the JSON means
 *                          the usual weight x reps
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
        String gifUrl,
        TrackingType trackingType
) {
    public HevyExerciseCatalogItem {
        if (trackingType == null) {
            trackingType = TrackingType.WEIGHT_REPS;
        }
    }
}
