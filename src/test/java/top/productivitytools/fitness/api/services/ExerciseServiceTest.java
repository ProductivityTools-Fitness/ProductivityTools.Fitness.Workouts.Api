package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.dto.responses.ExerciseHistoryEntryDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.entities.Workout;
import top.productivitytools.fitness.api.entities.WorkoutExercise;
import top.productivitytools.fitness.api.entities.WorkoutSet;
import top.productivitytools.fitness.api.repositories.ExerciseImageRepository;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.repositories.WorkoutExerciseRepository;
import top.productivitytools.fitness.api.security.UserContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private ExerciseImageRepository exerciseImageRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;

    @InjectMocks
    private ExerciseService exerciseService;

    private FitnessUser user;

    @BeforeEach
    void setUp() {
        user = new FitnessUser();
        user.setId(1L);
        UserContext.setCurrentUser(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void getExerciseHistory_MapsWorkoutsAndSetsNewestFirst() {
        Exercise exercise = new Exercise();
        exercise.setId(10L);

        Workout newer = new Workout();
        newer.setId(200L);
        newer.setWorkoutNumber(2);
        newer.setTitle("Push B");
        newer.setStartTime(OffsetDateTime.parse("2026-09-20T10:00:00Z"));
        newer.setStatus("COMPLETED");
        WorkoutExercise weNewer = new WorkoutExercise();
        weNewer.setWorkout(newer);
        weNewer.setExercise(exercise);
        WorkoutSet s2 = weNewer.addSet(new BigDecimal("82.50"), 8);
        s2.setIsCompleted(true);
        WorkoutSet s1 = weNewer.addSet(new BigDecimal("80.00"), 10);
        s1.setIsCompleted(true);

        Workout older = new Workout();
        older.setId(100L);
        older.setWorkoutNumber(1);
        older.setTitle("Push A");
        older.setStartTime(OffsetDateTime.parse("2026-09-10T10:00:00Z"));
        older.setStatus("COMPLETED");
        WorkoutExercise weOlder = new WorkoutExercise();
        weOlder.setWorkout(older);
        weOlder.setExercise(exercise);
        weOlder.addSet(new BigDecimal("75.00"), 10).setIsCompleted(true);

        when(exerciseRepository.existsById(10L)).thenReturn(true);
        when(workoutExerciseRepository.findHistoryForExercise(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(weNewer, weOlder));

        List<ExerciseHistoryEntryDto> history = exerciseService.getExerciseHistory(10L, 20);

        assertEquals(2, history.size());
        assertEquals(200L, history.get(0).workoutId());
        assertEquals("Push B", history.get(0).workoutTitle());
        assertEquals(2, history.get(0).sets().size());
        assertEquals(new BigDecimal("82.50"), history.get(0).sets().get(0).weightKg());
        assertEquals(100L, history.get(1).workoutId());
        assertEquals(1, history.get(1).sets().size());
    }

    @Test
    void getExerciseHistory_WhenExerciseMissing_ThrowsNotFound() {
        when(exerciseRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> exerciseService.getExerciseHistory(99L, 20));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(workoutExerciseRepository, never()).findHistoryForExercise(any(), any(), any());
    }

    @Test
    void getExerciseHistory_WhenNotAuthenticated_ThrowsUnauthorized() {
        UserContext.clear();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> exerciseService.getExerciseHistory(10L, 20));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
