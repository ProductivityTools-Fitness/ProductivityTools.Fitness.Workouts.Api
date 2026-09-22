package top.productivitytools.fitness.api.dto.catalog;

import java.util.List;

/**
 * Exercise as returned by Fitness.Catalog.Api.
 *
 * <p>Mirrors {@code ExerciseSearchResultDto} on the catalogue side; both the search and the
 * single-exercise endpoint return this shape. The animation is not part of it - it is fetched
 * separately as raw bytes.
 */
public record CatalogExerciseDto(
        String exerciseId,
        String name,
        String primaryBodyPart,
        String primaryEquipment,
        String primaryMuscle,
        List<String> bodyParts,
        List<String> equipments,
        List<String> targetMuscles,
        List<String> secondaryMuscles,
        List<String> instructions,
        String imageFileName,
        boolean hasImage) {
}
