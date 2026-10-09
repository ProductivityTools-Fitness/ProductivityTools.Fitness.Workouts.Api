package top.productivitytools.fitness.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.dto.catalog.CatalogExerciseDto;
import top.productivitytools.fitness.api.dto.catalog.CatalogSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.ExerciseImage;
import top.productivitytools.fitness.api.repositories.ExerciseImageRepository;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Searching and importing exercises from Fitness.Catalog.Api.
 *
 * <p>Importing copies both the metadata and the animation into the local database, so that
 * viewing a workout later does not depend on the catalogue service being reachable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseImageRepository exerciseImageRepository;
    private final CatalogClient catalogClient;

    /**
     * Backfills metadata and animations from Fitness.Catalog.Api for any existing exercise rows
     * that already carry a {@code catalog_exercise_id} (preserving their {@code exercise.id} and
     * workout history links) but do not yet have a local image copy in {@code exercise_image}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncMissingCatalogImages() {
        List<Exercise> missingImages = exerciseRepository.findByCatalogExerciseIdIsNotNullAndImageFileNameIsNull();
        if (missingImages.isEmpty()) {
            return;
        }
        log.info("Synchronizing {} catalog exercises missing local animations from Fitness.Catalog.Api...", missingImages.size());
        int synced = 0;
        for (Exercise exercise : missingImages) {
            try {
                importExercise(exercise.getCatalogExerciseId());
                synced++;
            } catch (RuntimeException e) {
                log.warn("Could not synchronize catalog exercise {}: {}", exercise.getCatalogExerciseId(), e.getMessage());
            }
        }
        log.info("Synchronized {}/{} catalog exercises with Fitness.Catalog.Api.", synced, missingImages.size());
    }

    public List<CatalogSearchResultDto> searchExercises(String name, String bodyCategory,
                                                        String equipmentCategory, Integer limit) {
        List<CatalogExerciseDto> catalogItems =
                catalogClient.searchExercises(name, bodyCategory, equipmentCategory, limit);
        if (catalogItems.isEmpty()) {
            return List.of();
        }

        List<String> catalogIds = catalogItems.stream()
                .map(CatalogExerciseDto::exerciseId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Map<String, Long> alreadyImported = exerciseRepository.findByCatalogExerciseIdIn(catalogIds)
                .stream()
                .collect(Collectors.toMap(
                        Exercise::getCatalogExerciseId,
                        Exercise::getId,
                        (existing, replacement) -> existing
                ));

        return catalogItems.stream().map(item -> new CatalogSearchResultDto(
                item.exerciseId(),
                item.name(),
                first(item.bodyParts()),
                first(item.equipments()),
                first(item.targetMuscles()),
                item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of(),
                item.instructions() != null ? item.instructions() : List.of(),
                item.hasImage(),
                alreadyImported.containsKey(item.exerciseId()),
                alreadyImported.get(item.exerciseId())
        )).toList();
    }

    /**
     * Copies an exercise from the catalogue into the local database. Idempotent: importing the
     * same exercise twice returns the row created the first time. If an existing row has not yet
     * had its animation copied, refreshes its metadata and downloads the animation in-place.
     */
    @Transactional
    public Exercise importExercise(String catalogExerciseId) {
        if (catalogExerciseId == null || catalogExerciseId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog exercise ID must not be empty");
        }

        Optional<Exercise> existing = exerciseRepository.findByCatalogExerciseId(catalogExerciseId);
        if (existing.isPresent() && existing.get().getImageFileName() != null
                && exerciseImageRepository.findByExercise(existing.get()).isPresent()) {
            return existing.get();
        }

        CatalogExerciseDto item = catalogClient.getExercise(catalogExerciseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Exercise not found in the catalog with id: " + catalogExerciseId));

        // Reuse the existing row if present (preserving exercise.id for workout_exercise links),
        // or adopt a system exercise with the same name if one exists without a catalog ID.
        Exercise exercise = existing.orElseGet(() ->
                exerciseRepository.findAvailableExercisesByName(null, item.name()).stream()
                        .filter(e -> e.getCatalogExerciseId() == null)
                        .findFirst()
                        .orElseGet(Exercise::new));

        exercise.setCatalogExerciseId(item.exerciseId());
        exercise.setName(item.name());
        exercise.setBodyCategory(first(item.bodyParts()));
        exercise.setEquipmentCategory(first(item.equipments()));
        exercise.setTargetMuscle(first(item.targetMuscles()));
        exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
        exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
        exercise.setIsSystem(true);
        exercise.setUser(null);

        Exercise saved = exerciseRepository.save(exercise);
        copyImage(saved, catalogExerciseId, item.imageFileName());
        return saved;
    }

    /**
     * Streams the animation straight from the catalogue, without storing anything. Used for
     * previews in search results, where the exercise is by definition not imported yet.
     */
    public Optional<CatalogClient.CatalogImage> fetchCatalogImage(String catalogExerciseId) {
        return catalogClient.getImage(catalogExerciseId);
    }

    /**
     * Failure to fetch the animation is logged and swallowed: an exercise without its GIF is
     * still usable, and losing the whole import over a missing picture would be worse.
     */
    private void copyImage(Exercise exercise, String catalogExerciseId, String fileName) {
        try {
            Optional<CatalogClient.CatalogImage> image = catalogClient.getImage(catalogExerciseId);
            if (image.isEmpty()) {
                log.info("Catalog has no animation for {}, imported without one", catalogExerciseId);
                return;
            }

            ExerciseImage entity = exerciseImageRepository.findByExercise(exercise)
                    .orElseGet(ExerciseImage::new);
            entity.setExercise(exercise);
            entity.setFileName(fileName != null ? fileName : catalogExerciseId + ".gif");
            entity.setContentType(image.get().contentType());
            entity.setImageData(image.get().data());
            entity.setFileSizeBytes(image.get().data().length);
            exerciseImageRepository.save(entity);

            exercise.setImageFileName(entity.getFileName());
            exerciseRepository.save(exercise);
        } catch (RuntimeException e) {
            log.warn("Could not copy the animation for {}: {}", catalogExerciseId, e.getMessage());
        }
    }

    private static String first(List<String> values) {
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
