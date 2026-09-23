package top.productivitytools.fitness.api.services.hevy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import top.productivitytools.fitness.api.dto.hevy.HevyImportRequest;
import top.productivitytools.fitness.api.dto.hevy.HevyImportResponse;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.entities.TrackingType;
import top.productivitytools.fitness.api.entities.Workout;
import top.productivitytools.fitness.api.entities.WorkoutExercise;
import top.productivitytools.fitness.api.entities.WorkoutSet;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.repositories.WorkoutRepository;
import top.productivitytools.fitness.api.security.UserContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HevyImportService {

    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("^(.*?)\\s*\\((.*?)\\)$");

    private final HevyClient hevyClient;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;
    private final HevyExerciseCatalogService hevyExerciseCatalogService;

    @Transactional
    public HevyImportResponse importWorkouts(HevyImportRequest request) {
        FitnessUser currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        List<JsonNode> rawWorkouts = resolveWorkouts(request);
        if (rawWorkouts.isEmpty()) {
            return new HevyImportResponse(0, 0, 0, 0, "No workouts found to import.", List.of());
        }

        log.info("Processing {} workouts from Hevy for user {} ({})", rawWorkouts.size(), currentUser.getUsername(), currentUser.getEmail());

        // Sort chronologically (oldest first) so that workout numbers and sequence make chronological sense
        List<JsonNode> sortedWorkouts = new ArrayList<>(rawWorkouts);
        sortedWorkouts.sort(Comparator.comparing(this::extractStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        // Preload all catalog exercises (ExerciseDB mapped + unmapped standalone) into DB before processing workouts
        int preloadedCount = hevyExerciseCatalogService.preloadAllExercises();

        int currentMaxNumber = workoutRepository.findMaxWorkoutNumberByUserId(currentUser.getId());
        int importedCount = 0;
        int skippedCount = 0;
        int exercisesCreated = preloadedCount;
        List<String> importedTitles = new ArrayList<>();

        // Cache resolved exercises during this import run to reduce redundant database roundtrips
        Map<String, Exercise> exerciseCache = new HashMap<>();

        for (JsonNode workoutNode : sortedWorkouts) {
            OffsetDateTime startTime = extractStartTime(workoutNode);
            if (startTime == null) {
                startTime = OffsetDateTime.now(ZoneOffset.UTC);
            }

            // Deduplication: check if workout at this exact startTime already exists for user
            if (workoutRepository.existsByUserIdAndStartTime(currentUser.getId(), startTime)) {
                skippedCount++;
                continue;
            }

            OffsetDateTime endTime = parseDateTime(workoutNode, "end_time");
            int durationSeconds = extractDuration(workoutNode, startTime, endTime);

            String title = extractTitle(workoutNode, currentMaxNumber + 1);
            String notes = workoutNode.path("description").asText(null);

            Workout workout = new Workout();
            workout.setUser(currentUser);
            workout.setWorkoutNumber(++currentMaxNumber);
            workout.setTitle(title);
            workout.setStartTime(startTime);
            workout.setEndTime(endTime);
            workout.setDurationSeconds(durationSeconds);
            workout.setStatus("COMPLETED");
            workout.setNotes(notes);

            JsonNode exercisesNode = workoutNode.path("exercises");
            int orderIndex = 1;
            if (exercisesNode.isArray()) {
                for (JsonNode exNode : exercisesNode) {
                    Exercise exercise = resolveOrCreateExercise(exNode, currentUser, exerciseCache);
                    if (exercise.getId() == null) {
                        exercise = exerciseRepository.save(exercise);
                        exercisesCreated++;
                    }

                    WorkoutExercise we = new WorkoutExercise();
                    we.setWorkout(workout);
                    we.setExercise(exercise);
                    we.setOrderIndex(orderIndex++);
                    we.setNotes(exNode.path("notes").asText(null));
                    int defaultRest = (currentUser.getDefaultRestTimerSeconds() != null) ? currentUser.getDefaultRestTimerSeconds() : 90;
                    we.setRestTimerSeconds(defaultRest);

                    JsonNode setsNode = exNode.path("sets");
                    int setNumber = 1;
                    if (setsNode.isArray() && !setsNode.isEmpty()) {
                        for (JsonNode setNode : setsNode) {
                            WorkoutSet ws = new WorkoutSet();
                            ws.setWorkoutExercise(we);
                            ws.setSetNumber(setNumber++);
                            double weightKg = setNode.path("weight_kg").asDouble(0.0);
                            ws.setWeightKg(BigDecimal.valueOf(weightKg));
                            ws.setReps(setNode.path("reps").asInt(0));
                            ws.setDurationSeconds(readInteger(setNode, "duration_seconds"));
                            ws.setDistanceMeters(readDecimal(setNode, "distance_meters"));
                            ws.setIsCompleted(true);
                            we.getSets().add(ws);
                        }
                    } else {
                        // Fallback set
                        WorkoutSet ws = new WorkoutSet();
                        ws.setWorkoutExercise(we);
                        ws.setSetNumber(1);
                        ws.setWeightKg(BigDecimal.ZERO);
                        ws.setReps(0);
                        ws.setIsCompleted(true);
                        we.getSets().add(ws);
                    }

                    // An exercise imported before this feature existed, or created from a mapping
                    // that does not declare a type, still carries the WEIGHT_REPS default. If the
                    // sets themselves show a stopwatch or a distance, correct the exercise once.
                    TrackingType detected = detectTrackingType(we.getSets());
                    if (detected != TrackingType.WEIGHT_REPS
                            && exercise.getTrackingType() == TrackingType.WEIGHT_REPS) {
                        exercise.setTrackingType(detected);
                        exerciseRepository.save(exercise);
                    }

                    workout.getExercises().add(we);
                }
            }

            workoutRepository.save(workout);
            importedCount++;
            importedTitles.add(workout.getTitle() + " (" + workout.getStartTime().toLocalDate() + ")");
        }

        String summary = String.format("Successfully imported %d workouts (%d skipped as duplicates, %d new exercises registered).",
                importedCount, skippedCount, exercisesCreated);
        log.info(summary);

        return new HevyImportResponse(
                rawWorkouts.size(),
                importedCount,
                skippedCount,
                exercisesCreated,
                summary,
                importedTitles
        );
    }

    private List<JsonNode> resolveWorkouts(HevyImportRequest request) {
        String rawToken = (request != null) ? request.accessToken() : null;
        String token = hevyClient.resolveToken(rawToken);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "access-token is required. Pass {\"access-token\": \"...\"} in request body.");
        }
        return hevyClient.fetchWorkouts(token);
    }

    private Exercise resolveOrCreateExercise(JsonNode exNode, FitnessUser user, Map<String, Exercise> cache) {
        String title = exNode.path("title").asText("Exercise").trim();
        String templateId = exNode.path("exercise_template_id").asText(null);
        if (templateId != null && templateId.isBlank()) {
            templateId = null;
        }

        String cacheKey = (templateId != null) ? "TPL:" + templateId : "NAME:" + title.toLowerCase();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        // 1. Check catalog mapping for Hevy title (maps to ExerciseDB exercise or standalone exercise)
        Optional<HevyExerciseCatalogItem> catalogOpt = hevyExerciseCatalogService.findMapping(title);
        if (catalogOpt.isPresent()) {
            HevyExerciseCatalogItem mapping = catalogOpt.get();
            if (mapping.catalogExerciseId() != null && !mapping.catalogExerciseId().isBlank()) {
                Optional<Exercise> byCatalogId = exerciseRepository.findByCatalogExerciseId(mapping.catalogExerciseId());
                if (byCatalogId.isPresent()) {
                    Exercise found = byCatalogId.get();
                    cache.put(cacheKey, found);
                    return found;
                }
            }

            List<Exercise> byMappedName = exerciseRepository.findAvailableExercisesByName(user.getId(), mapping.name());
            if (!byMappedName.isEmpty()) {
                Exercise found = byMappedName.get(0);
                cache.put(cacheKey, found);
                return found;
            }

            // Fallback: create from catalog mapping if not yet in database
            Exercise mappedEx = new Exercise();
            mappedEx.setName(mapping.name());
            mappedEx.setCatalogExerciseId(mapping.catalogExerciseId());
            mappedEx.setIsSystem(true);
            mappedEx.setUser(null);
            mappedEx.setBodyCategory(mapping.bodyCategory());
            mappedEx.setEquipmentCategory(mapping.equipmentCategory());
            mappedEx.setTargetMuscle(mapping.targetMuscle());
            mappedEx.setSecondaryMuscles(mapping.secondaryMuscles() != null ? mapping.secondaryMuscles() : List.of());
            mappedEx.setInstructions(mapping.instructions() != null ? mapping.instructions() : List.of());
            mappedEx.setGifUrl(mapping.gifUrl());
            mappedEx.setTrackingType(mapping.trackingType());
            mappedEx = exerciseRepository.save(mappedEx);
            cache.put(cacheKey, mappedEx);
            return mappedEx;
        }

        // 2. Check by template ID if present
        if (templateId != null) {
            Optional<Exercise> byTpl = exerciseRepository.findByCatalogExerciseId(templateId);
            if (byTpl.isPresent()) {
                cache.put(cacheKey, byTpl.get());
                return byTpl.get();
            }
        }

        // 3. Check by exact name for user or system
        List<Exercise> byName = exerciseRepository.findAvailableExercisesByName(user.getId(), title);
        if (!byName.isEmpty()) {
            Exercise found = byName.get(0);
            cache.put(cacheKey, found);
            return found;
        }

        // 4. Check by normalized alias: "Deadlift (Barbell)" -> "barbell deadlift"
        String alias = normalizeExerciseAlias(title);
        if (alias != null) {
            List<Exercise> byAlias = exerciseRepository.findAvailableExercisesByName(user.getId(), alias);
            if (!byAlias.isEmpty()) {
                Exercise found = byAlias.get(0);
                cache.put(cacheKey, found);
                return found;
            }
        }

        // 5. Create new Exercise for user (standalone fallback for uncataloged exercises)
        Exercise newEx = new Exercise();
        newEx.setName(title);
        newEx.setCatalogExerciseId(templateId);
        newEx.setUser(user);
        newEx.setIsSystem(false);
        newEx.setEquipmentCategory(exNode.path("equipment_category").asText(null));
        String muscle = exNode.path("muscle_group").asText(null);
        newEx.setTargetMuscle(muscle);
        newEx.setBodyCategory(muscle);
        newEx.setGifUrl(exNode.path("thumbnail_url").asText(null));

        cache.put(cacheKey, newEx);
        return newEx;
    }

    /**
     * Hevy sends an explicit {@code null} for fields that do not apply to a set, so a plain
     * {@code asInt(0)} would turn "not measured" into a real zero.
     */
    private Integer readInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    /** Decimal counterpart of {@link #readInteger}. */
    private BigDecimal readDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(value.asDouble());
    }

    /**
     * Derives how an exercise is measured from the sets Hevy recorded for it.
     *
     * <p>Deliberately never returns {@code REPS_ONLY}: a bodyweight exercise logged at 0 kg is
     * indistinguishable from a warm-up with an empty bar, and guessing wrong would hide the
     * weight column. Reps-only exercises are declared in the catalogue instead.
     */
    private TrackingType detectTrackingType(List<WorkoutSet> sets) {
        boolean hasDuration = false;
        boolean hasDistance = false;
        boolean hasWeight = false;

        for (WorkoutSet set : sets) {
            if (set.getDurationSeconds() != null && set.getDurationSeconds() > 0) {
                hasDuration = true;
            }
            if (set.getDistanceMeters() != null && set.getDistanceMeters().signum() > 0) {
                hasDistance = true;
            }
            if (set.getWeightKg() != null && set.getWeightKg().signum() > 0) {
                hasWeight = true;
            }
        }

        if (hasDistance) {
            return TrackingType.DISTANCE_DURATION;
        }
        if (hasDuration) {
            return hasWeight ? TrackingType.DURATION_WEIGHT : TrackingType.DURATION;
        }
        return TrackingType.WEIGHT_REPS;
    }

    private String normalizeExerciseAlias(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = PARENTHESIS_PATTERN.matcher(title.trim());
        if (matcher.matches()) {
            String exerciseName = matcher.group(1).trim().toLowerCase();
            String equipment = matcher.group(2).trim().toLowerCase();
            return equipment + " " + exerciseName;
        }
        return null;
    }

    private OffsetDateTime extractStartTime(JsonNode workoutNode) {
        OffsetDateTime st = parseDateTime(workoutNode, "start_time");
        if (st == null) {
            st = parseDateTime(workoutNode, "created_at");
        }
        return st;
    }

    private int extractDuration(JsonNode workoutNode, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (workoutNode.hasNonNull("duration_seconds")) {
            return workoutNode.get("duration_seconds").asInt();
        }
        if (startTime != null && endTime != null) {
            return (int) Math.max(0, Duration.between(startTime, endTime).getSeconds());
        }
        return 0;
    }

    private String extractTitle(JsonNode workoutNode, int fallbackNumber) {
        if (workoutNode.hasNonNull("name") && !workoutNode.get("name").asText().isBlank()) {
            return workoutNode.get("name").asText().trim();
        }
        if (workoutNode.hasNonNull("title") && !workoutNode.get("title").asText().isBlank()) {
            return workoutNode.get("title").asText().trim();
        }
        return "Trening #" + fallbackNumber;
    }

    private OffsetDateTime parseDateTime(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode val = node.get(fieldName);
        if (val.isNumber()) {
            long sec = val.asLong();
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(sec), ZoneOffset.UTC);
        }
        String text = val.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.matches("^\\d+$")) {
            long sec = Long.parseLong(text);
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(sec), ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception e) {
            try {
                return Instant.parse(text).atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
                log.warn("Could not parse date '{}' for field '{}'", text, fieldName);
                return null;
            }
        }
    }
}
