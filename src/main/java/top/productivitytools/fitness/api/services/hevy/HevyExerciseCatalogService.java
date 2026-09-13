package top.productivitytools.fitness.api.services.hevy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import top.productivitytools.fitness.api.entities.Exercise;
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
            if (item.externalExerciseId() != null && !item.externalExerciseId().isBlank()) {
                Optional<Exercise> byExtId = exerciseRepository.findByExternalExerciseId(item.externalExerciseId());
                if (byExtId.isPresent()) {
                    continue;
                }
                List<Exercise> byName = exerciseRepository.findAvailableExercisesByName(null, item.name());
                if (!byName.isEmpty()) {
                    continue;
                }

                Exercise exercise = new Exercise();
                exercise.setName(item.name());
                exercise.setExternalExerciseId(item.externalExerciseId());
                exercise.setIsSystem(true);
                exercise.setUser(null);
                exercise.setBodyCategory(item.bodyCategory());
                exercise.setEquipmentCategory(item.equipmentCategory());
                exercise.setTargetMuscle(item.targetMuscle());
                exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
                exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
                exercise.setGifUrl(item.gifUrl());
                exerciseRepository.save(exercise);
                createdCount++;
            } else {
                // Standalone unmapped exercise
                List<Exercise> byName = exerciseRepository.findAvailableExercisesByName(null, item.name());
                if (!byName.isEmpty()) {
                    continue;
                }

                Exercise exercise = new Exercise();
                exercise.setName(item.name());
                exercise.setExternalExerciseId(null);
                exercise.setIsSystem(true);
                exercise.setUser(null);
                exercise.setBodyCategory(item.bodyCategory());
                exercise.setEquipmentCategory(item.equipmentCategory());
                exercise.setTargetMuscle(item.targetMuscle());
                exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
                exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
                exercise.setGifUrl(null);
                exerciseRepository.save(exercise);
                createdCount++;
            }
        }

        if (createdCount > 0) {
            log.info("Preloaded {} new exercises from catalog into database.", createdCount);
        }
        return createdCount;
    }
}
