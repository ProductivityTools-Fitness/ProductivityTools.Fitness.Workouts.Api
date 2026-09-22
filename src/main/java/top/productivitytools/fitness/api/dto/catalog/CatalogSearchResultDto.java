package top.productivitytools.fitness.api.dto.catalog;

import java.util.List;

/**
 * A single row of the catalogue search results, as served to the web client.
 *
 * <p>Flattens the catalogue's multi-valued body part / equipment / muscle lists down to the
 * first entry, because that is what the local {@code Exercise} entity stores.
 *
 * <p>There is no image URL here: the client builds it from {@code catalogExerciseId}, hitting
 * {@code /api/catalog/{catalogExerciseId}/image} for a preview and
 * {@code /api/exercise/{localExerciseId}/image} once the exercise has been imported.
 *
 * @param isAlreadyImported whether this exercise already exists in the local database
 * @param localExerciseId   local numeric id when imported, otherwise null
 */
public record CatalogSearchResultDto(
        String catalogExerciseId,
        String name,
        String bodyCategory,
        String equipmentCategory,
        String targetMuscle,
        List<String> secondaryMuscles,
        List<String> instructions,
        boolean hasImage,
        boolean isAlreadyImported,
        Long localExerciseId) {
}
