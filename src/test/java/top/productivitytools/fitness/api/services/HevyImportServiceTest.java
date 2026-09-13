package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.productivitytools.fitness.api.dto.hevy.HevyImportRequest;
import top.productivitytools.fitness.api.dto.hevy.HevyImportResponse;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.entities.Workout;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.repositories.WorkoutRepository;
import top.productivitytools.fitness.api.security.UserContext;
import top.productivitytools.fitness.api.services.hevy.HevyClient;
import top.productivitytools.fitness.api.services.hevy.HevyImportService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HevyImportServiceTest {

    @Mock
    private HevyClient hevyClient;

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogService hevyExerciseCatalogService;

    @InjectMocks
    private HevyImportService hevyImportService;

    private FitnessUser user;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        user = new FitnessUser();
        user.setId(1L);
        user.setEmail("pwujczyk@gmail.com");
        user.setUsername("pwujczyk");
        user.setDefaultRestTimerSeconds(90);
        UserContext.setCurrentUser(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void importWorkouts_WhenNotAuthenticated_ThrowsUnauthorized() {
        UserContext.clear();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                hevyImportService.importWorkouts(new HevyImportRequest("test-token")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void importWorkouts_WhenNoAccessTokenProvided_ThrowsBadRequest() {
        when(hevyClient.resolveToken(null)).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                hevyImportService.importWorkouts(new HevyImportRequest(null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void requestDeserialization_OnlyAcceptsAccessTokenKey() throws Exception {
        String validJson = "{\"access-token\":\"my-secret-token\"}";
        HevyImportRequest req = objectMapper.readValue(validJson, HevyImportRequest.class);
        assertEquals("my-secret-token", req.accessToken());

        String invalidJson = "{\"accessToken\":\"my-secret-token\"}";
        HevyImportRequest req2 = objectMapper.readValue(invalidJson, HevyImportRequest.class);
        assertNull(req2.accessToken());
    }

    @Test
    void importWorkouts_WithValidAccessToken_ImportsWorkoutsAndExercises() {
        Map<String, Object> set1 = Map.of("index", 0, "weight_kg", 50.0, "reps", 10);
        Map<String, Object> set2 = Map.of("index", 1, "weight_kg", 55.0, "reps", 8);
        Map<String, Object> ex = Map.of(
                "title", "Overhead Press (Barbell)",
                "exercise_template_id", "OHP_123",
                "sets", List.of(set1, set2)
        );
        Map<String, Object> workoutData = Map.of(
                "name", "Push Day",
                "start_time", 1700000000,
                "end_time", 1700003600,
                "description", "Great session",
                "exercises", List.of(ex)
        );

        JsonNode workoutNode = objectMapper.valueToTree(workoutData);

        when(hevyClient.resolveToken("test-token")).thenReturn("test-token");
        when(hevyClient.fetchWorkouts("test-token")).thenReturn(List.of(workoutNode));
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(0);
        when(workoutRepository.existsByUserIdAndStartTime(eq(1L), any(OffsetDateTime.class))).thenReturn(false);
        when(exerciseRepository.findByExternalExerciseId("OHP_123")).thenReturn(Optional.empty());
        when(exerciseRepository.findAvailableExercisesByName(eq(1L), anyString())).thenReturn(List.of());
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> {
            Exercise e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        HevyImportRequest request = new HevyImportRequest("test-token");
        HevyImportResponse response = hevyImportService.importWorkouts(request);

        assertNotNull(response);
        assertEquals(1, response.totalFetched());
        assertEquals(1, response.workoutsImported());
        assertEquals(0, response.workoutsSkipped());
        assertEquals(1, response.exercisesCreated());

        verify(workoutRepository).save(argThat(w -> {
            assertEquals("Push Day", w.getTitle());
            assertEquals(1, w.getWorkoutNumber());
            assertEquals("COMPLETED", w.getStatus());
            assertEquals(3600, w.getDurationSeconds());
            assertEquals(1, w.getExercises().size());
            assertEquals(2, w.getExercises().get(0).getSets().size());
            assertEquals(BigDecimal.valueOf(50.0), w.getExercises().get(0).getSets().get(0).getWeightKg());
            assertEquals(10, w.getExercises().get(0).getSets().get(0).getReps());
            return true;
        }));
    }

    @Test
    void importWorkouts_WhenDuplicate_SkipsImport() {
        Map<String, Object> workoutData = Map.of(
                "name", "Morning Workout",
                "start_time", 1700000000,
                "exercises", List.of()
        );

        JsonNode workoutNode = objectMapper.valueToTree(workoutData);

        when(hevyClient.resolveToken("test-token")).thenReturn("test-token");
        when(hevyClient.fetchWorkouts("test-token")).thenReturn(List.of(workoutNode));
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(5);
        when(workoutRepository.existsByUserIdAndStartTime(eq(1L), any(OffsetDateTime.class))).thenReturn(true);

        HevyImportRequest request = new HevyImportRequest("test-token");
        HevyImportResponse response = hevyImportService.importWorkouts(request);

        assertEquals(1, response.totalFetched());
        assertEquals(0, response.workoutsImported());
        assertEquals(1, response.workoutsSkipped());
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void importWorkouts_WhenExerciseMatchesExistingSystemExercise_ReusesIt() {
        Exercise systemEx = new Exercise();
        systemEx.setId(2L);
        systemEx.setName("barbell deadlift");
        systemEx.setIsSystem(true);

        Map<String, Object> ex = Map.of(
                "title", "Deadlift (Barbell)",
                "sets", List.of(Map.of("weight_kg", 100.0, "reps", 5))
        );
        Map<String, Object> workoutData = Map.of(
                "name", "Legs",
                "start_time", 1700000000,
                "exercises", List.of(ex)
        );

        JsonNode workoutNode = objectMapper.valueToTree(workoutData);

        when(hevyClient.resolveToken("test-token")).thenReturn("test-token");
        when(hevyClient.fetchWorkouts("test-token")).thenReturn(List.of(workoutNode));
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(0);
        when(workoutRepository.existsByUserIdAndStartTime(eq(1L), any(OffsetDateTime.class))).thenReturn(false);
        // "Deadlift (Barbell)" -> normalized alias "barbell deadlift" matches
        when(exerciseRepository.findAvailableExercisesByName(1L, "Deadlift (Barbell)")).thenReturn(List.of());
        when(exerciseRepository.findAvailableExercisesByName(1L, "barbell deadlift")).thenReturn(List.of(systemEx));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        HevyImportRequest request = new HevyImportRequest("test-token");
        HevyImportResponse response = hevyImportService.importWorkouts(request);

        assertEquals(1, response.workoutsImported());
        assertEquals(0, response.exercisesCreated());
        verify(exerciseRepository, never()).save(any());
        verify(workoutRepository).save(argThat(w ->
                w.getExercises().get(0).getExercise().getId().equals(2L)
        ));
    }

    @Test
    void importWorkouts_PreloadsExercisesAndUsesCatalogMapping_ForMappedExercise() {
        Exercise systemBench = new Exercise();
        systemBench.setId(10L);
        systemBench.setName("barbell bench press");
        systemBench.setExternalExerciseId("EIeI8Vf");
        systemBench.setIsSystem(true);

        top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogItem catalogItem =
                new top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogItem(
                        "Bench Press (Barbell)", "Wyciskanie leżąc (sztanga)", "barbell bench press",
                        "EIeI8Vf", "barbell", "chest", "pectorals", List.of("triceps"), List.of(), "http://gif"
                );

        Map<String, Object> ex = Map.of(
                "title", "Bench Press (Barbell)",
                "sets", List.of(Map.of("weight_kg", 80.0, "reps", 8))
        );
        Map<String, Object> workoutData = Map.of(
                "name", "Chest Day",
                "start_time", 1700000000,
                "exercises", List.of(ex)
        );

        JsonNode workoutNode = objectMapper.valueToTree(workoutData);

        when(hevyClient.resolveToken("test-token")).thenReturn("test-token");
        when(hevyClient.fetchWorkouts("test-token")).thenReturn(List.of(workoutNode));
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(0);
        when(workoutRepository.existsByUserIdAndStartTime(eq(1L), any(OffsetDateTime.class))).thenReturn(false);
        when(hevyExerciseCatalogService.preloadAllExercises()).thenReturn(64);
        when(hevyExerciseCatalogService.findMapping("Bench Press (Barbell)")).thenReturn(Optional.of(catalogItem));
        when(exerciseRepository.findByExternalExerciseId("EIeI8Vf")).thenReturn(Optional.of(systemBench));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        HevyImportRequest request = new HevyImportRequest("test-token");
        HevyImportResponse response = hevyImportService.importWorkouts(request);

        assertEquals(1, response.workoutsImported());
        assertEquals(64, response.exercisesCreated());
        verify(hevyExerciseCatalogService).preloadAllExercises();
        verify(workoutRepository).save(argThat(w ->
                w.getExercises().get(0).getExercise().getId().equals(10L) &&
                w.getExercises().get(0).getExercise().getName().equals("barbell bench press")
        ));
    }

    @Test
    void importWorkouts_PreloadsExercisesAndUsesCatalogMapping_ForUnmappedStandaloneExercise() {
        Exercise standaloneRowing = new Exercise();
        standaloneRowing.setId(20L);
        standaloneRowing.setName("Rowing Machine");
        standaloneRowing.setIsSystem(true);

        top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogItem catalogItem =
                new top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogItem(
                        "Rowing Machine", "Wioślarz (maszyna)", "Rowing Machine",
                        null, "machine", "cardio", "cardio", List.of(), List.of(), null
                );

        Map<String, Object> ex = Map.of(
                "title", "Rowing Machine",
                "sets", List.of(Map.of("weight_kg", 0.0, "reps", 100))
        );
        Map<String, Object> workoutData = Map.of(
                "name", "Cardio Session",
                "start_time", 1700000000,
                "exercises", List.of(ex)
        );

        JsonNode workoutNode = objectMapper.valueToTree(workoutData);

        when(hevyClient.resolveToken("test-token")).thenReturn("test-token");
        when(hevyClient.fetchWorkouts("test-token")).thenReturn(List.of(workoutNode));
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(0);
        when(workoutRepository.existsByUserIdAndStartTime(eq(1L), any(OffsetDateTime.class))).thenReturn(false);
        when(hevyExerciseCatalogService.preloadAllExercises()).thenReturn(0);
        when(hevyExerciseCatalogService.findMapping("Rowing Machine")).thenReturn(Optional.of(catalogItem));
        when(exerciseRepository.findAvailableExercisesByName(1L, "Rowing Machine")).thenReturn(List.of(standaloneRowing));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        HevyImportRequest request = new HevyImportRequest("test-token");
        HevyImportResponse response = hevyImportService.importWorkouts(request);

        assertEquals(1, response.workoutsImported());
        verify(workoutRepository).save(argThat(w ->
                w.getExercises().get(0).getExercise().getId().equals(20L) &&
                w.getExercises().get(0).getExercise().getName().equals("Rowing Machine")
        ));
    }
}
