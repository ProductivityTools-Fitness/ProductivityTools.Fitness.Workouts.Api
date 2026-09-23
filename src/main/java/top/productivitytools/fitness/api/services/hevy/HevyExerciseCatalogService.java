package top.productivitytools.fitness.api.services.hevy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.TrackingType;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class HevyExerciseCatalogService {

    private static final String CATALOG_RESOURCE_PATH = "/hevy_exercise_catalog.json";

    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<HevyExerciseCatalogItem> catalogItems = new ArrayList<>();
    private final Map<String, HevyExerciseCatalogItem> titleIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadCatalog();
    }

    public synchronized void loadCatalog() {
        try (InputStream is = getClass().getResourceAsStream(CATALOG_RESOURCE_PATH)) {
            if (is == null) {
                log.warn("Exercise catalog resource not found at: {}", CATALOG_RESOURCE_PATH);
                return;
            }
            List<HevyExerciseCatalogItem> loaded = objectMapper.readValue(is, new TypeReference<List<HevyExerciseCatalogItem>>() {});
            catalogItems.clear();
            titleIndex.clear();
            if (loaded != null) {
                catalogItems.addAll(loaded);
                for (HevyExerciseCatalogItem item : loaded) {
                    if (item.hevyTitle() != null && !item.hevyTitle().isBlank()) {
                        titleIndex.put(item.hevyTitle().trim().toLowerCase(), item);
                    }
                    if (item.hevyPlTitle() != null && !item.hevyPlTitle().isBlank()) {
                        titleIndex.put(item.hevyPlTitle().trim().toLowerCase(), item);
                    }
                    if (item.name() != null && !item.name().isBlank()) {
                        titleIndex.put(item.name().trim().toLowerCase(), item);
                    }
                }
            }
            log.info("Loaded {} exercise mappings from {}", catalogItems.size(), CATALOG_RESOURCE_PATH);
        } catch (Exception e) {
            log.error("Failed to load exercise catalog from {}", CATALOG_RESOURCE_PATH, e);
            throw new IllegalStateException("Could not load exercise catalog: " + CATALOG_RESOURCE_PATH, e);
        }
    }

    public Optional<HevyExerciseCatalogItem> findMapping(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        HevyExerciseCatalogItem item = titleIndex.get(title.trim().toLowerCase());
        return Optional.ofNullable(item);
    }

    public List<HevyExerciseCatalogItem> getAllCatalogItems() {
        return Collections.unmodifiableList(catalogItems);
    }

    @Transactional
    public int preloadAllExercises() {
        if (catalogItems.isEmpty()) {
            loadCatalog();
        }

        int createdCount = 0;
        for (HevyExerciseCatalogItem item : catalogItems) {
            if (item.catalogExerciseId() != null && !item.catalogExerciseId().isBlank()) {
                Optional<Exercise> byCatalogId = exerciseRepository.findByCatalogExerciseId(item.catalogExerciseId());
                if (byCatalogId.isPresent()) {
                    syncTrackingType(byCatalogId.get(), item);
                    continue;
                }
                List<Exercise> byName = exerciseRepository.findAvailableExercisesByName(null, item.name());
                if (!byName.isEmpty()) {
                    syncTrackingType(byName.get(0), item);
                    continue;
                }

                Exercise exercise = new Exercise();
                exercise.setName(item.name());
                exercise.setCatalogExerciseId(item.catalogExerciseId());
                exercise.setIsSystem(true);
                exercise.setUser(null);
                exercise.setBodyCategory(item.bodyCategory());
                exercise.setEquipmentCategory(item.equipmentCategory());
                exercise.setTargetMuscle(item.targetMuscle());
                exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
                exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
                exercise.setGifUrl(item.gifUrl());
                exercise.setTrackingType(item.trackingType());
                exerciseRepository.save(exercise);
                createdCount++;
            } else {
                // Standalone unmapped exercise
                List<Exercise> byName = exerciseRepository.findAvailableExercisesByName(null, item.name());
                if (!byName.isEmpty()) {
                    syncTrackingType(byName.get(0), item);
                    continue;
                }

                Exercise exercise = new Exercise();
                exercise.setName(item.name());
                exercise.setCatalogExerciseId(null);
                exercise.setIsSystem(true);
                exercise.setUser(null);
                exercise.setBodyCategory(item.bodyCategory());
                exercise.setEquipmentCategory(item.equipmentCategory());
                exercise.setTargetMuscle(item.targetMuscle());
                exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
                exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
                exercise.setGifUrl(null);
                exercise.setTrackingType(item.trackingType());
                exerciseRepository.save(exercise);
                createdCount++;
            }
        }

        if (createdCount > 0) {
            log.info("Preloaded {} new exercises from catalog into database.", createdCount);
        }
        return createdCount;
    }

    /**
     * Brings an exercise that already exists in the database in line with the mapping file.
     *
     * <p>Only ever moves away from the {@code WEIGHT_REPS} default: exercises preloaded before
     * tracking types existed all carry it, whereas a type set by a Hevy import or by hand is a
     * deliberate value and must survive.
     */
    private void syncTrackingType(Exercise exercise, HevyExerciseCatalogItem item) {
        if (item.trackingType() == TrackingType.WEIGHT_REPS) {
            return;
        }
        if (exercise.getTrackingType() != TrackingType.WEIGHT_REPS) {
            return;
        }
        exercise.setTrackingType(item.trackingType());
        exerciseRepository.save(exercise);
        log.info("Updated tracking type of '{}' to {}", exercise.getName(), item.trackingType());
    }
}
