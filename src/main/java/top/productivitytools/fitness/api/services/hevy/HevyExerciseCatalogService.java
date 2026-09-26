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
            Optional<Exercise> existing = findExistingExerciseForItem(item);
            if (existing.isPresent()) {
                syncCatalogFields(existing.get(), item);
                continue;
            }

            Exercise exercise = new Exercise();
            applyCatalogFields(exercise, item);
            exercise.setIsSystem(true);
            exercise.setUser(null);
            exerciseRepository.save(exercise);
            createdCount++;
        }

        if (createdCount > 0) {
            log.info("Preloaded {} new exercises from catalog into database.", createdCount);
        }
        return createdCount;
    }

    private Optional<Exercise> findExistingExerciseForItem(HevyExerciseCatalogItem item) {
        if (item.catalogExerciseId() != null && !item.catalogExerciseId().isBlank()) {
            Optional<Exercise> byCatalogId = exerciseRepository.findByCatalogExerciseId(item.catalogExerciseId());
            if (byCatalogId.isPresent()) {
                return byCatalogId;
            }
        }
        for (String candidateName : Arrays.asList(item.name(), item.hevyTitle(), item.hevyPlTitle())) {
            if (candidateName != null && !candidateName.isBlank()) {
                List<Exercise> byName = exerciseRepository.findAvailableExercisesByName(null, candidateName);
                if (!byName.isEmpty()) {
                    return Optional.of(byName.get(0));
                }
            }
        }
        if ("Plank".equalsIgnoreCase(item.hevyTitle())) {
            Optional<Exercise> legacyPlank = exerciseRepository.findByCatalogExerciseId("fp_weighted_front_plank");
            if (legacyPlank.isPresent()) {
                return legacyPlank;
            }
            List<Exercise> legacyByName = exerciseRepository.findAvailableExercisesByName(null, "weighted front plank");
            if (!legacyByName.isEmpty()) {
                return Optional.of(legacyByName.get(0));
            }
        }
        return Optional.empty();
    }

    private void applyCatalogFields(Exercise exercise, HevyExerciseCatalogItem item) {
        String cleanCatalogId = (item.catalogExerciseId() != null && !item.catalogExerciseId().isBlank())
                ? item.catalogExerciseId()
                : null;
        exercise.setName(item.name());
        exercise.setCatalogExerciseId(cleanCatalogId);
        exercise.setBodyCategory(item.bodyCategory());
        exercise.setEquipmentCategory(item.equipmentCategory());
        exercise.setTargetMuscle(item.targetMuscle());
        exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
        exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
        exercise.setGifUrl(cleanCatalogId != null ? item.gifUrl() : null);
        if (exercise.getTrackingType() == null || exercise.getTrackingType() == TrackingType.WEIGHT_REPS
                || item.trackingType() != TrackingType.WEIGHT_REPS) {
            exercise.setTrackingType(item.trackingType());
        }
        if (exercise.getId() == null && (exercise.getWakeLockSentinel() == null || !exercise.getWakeLockSentinel())) {
            boolean isTimed = item.trackingType() == TrackingType.DURATION
                    || item.trackingType() == TrackingType.DURATION_WEIGHT
                    || item.trackingType() == TrackingType.DISTANCE_DURATION;
            exercise.setWakeLockSentinel(isTimed);
        }
    }

    private void syncCatalogFields(Exercise exercise, HevyExerciseCatalogItem item) {
        String cleanCatalogId = (item.catalogExerciseId() != null && !item.catalogExerciseId().isBlank())
                ? item.catalogExerciseId()
                : null;
        boolean changed = !Objects.equals(exercise.getName(), item.name())
                || !Objects.equals(exercise.getCatalogExerciseId(), cleanCatalogId)
                || (item.trackingType() != TrackingType.WEIGHT_REPS && exercise.getTrackingType() != item.trackingType());
        if (changed) {
            applyCatalogFields(exercise, item);
            exerciseRepository.save(exercise);
            log.info("Synchronized exercise '{}' (catalogExerciseId={})", exercise.getName(), exercise.getCatalogExerciseId());
        }
    }
}
