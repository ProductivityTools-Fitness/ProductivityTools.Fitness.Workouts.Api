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
import top.productivitytools.fitness.api.dto.requests.AddExercisesRequest;
import top.productivitytools.fitness.api.dto.requests.AddSetRequest;
import top.productivitytools.fitness.api.dto.responses.WorkoutSummaryDto;
import top.productivitytools.fitness.api.entities.*;
import top.productivitytools.fitness.api.repositories.*;
import top.productivitytools.fitness.api.security.UserContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private FitnessUserRepository userRepository;
    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private WorkoutSetRepository workoutSetRepository;

    @InjectMocks
    private WorkoutService workoutService;

    private FitnessUser user;
    private Exercise exercise;
    private Workout currentWorkout;

    @BeforeEach
    void setUp() {
        user = new FitnessUser();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setDefaultRestTimerSeconds(60);
        UserContext.setCurrentUser(user);

        exercise = new Exercise();
        exercise.setId(10L);
        exercise.setName("Bench Press");

        currentWorkout = new Workout();
        currentWorkout.setId(100L);
        currentWorkout.setUser(user);
        currentWorkout.setStartTime(OffsetDateTime.now());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void addExercisesToWorkout_WhenNeverDoneBefore_CreatesSingleInitialSetWithNullPrev() {
        when(workoutRepository.findById(100L)).thenReturn(Optional.of(currentWorkout));
        when(exerciseRepository.findById(10L)).thenReturn(Optional.of(exercise));
        when(workoutExerciseRepository.findPastExercises(eq(1L), eq(10L), eq(100L), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddExercisesRequest request = new AddExercisesRequest(100L, List.of(10L));
        Workout result = workoutService.addExercisesToWorkout(100L, request);

        assertNotNull(result);
        assertEquals(1, result.getExercises().size());
        WorkoutExercise we = result.getExercises().get(0);
        assertEquals(1, we.getSets().size());

        WorkoutSet set = we.getSets().get(0);
        assertEquals(1, set.getSetNumber());
        assertEquals(BigDecimal.ZERO, set.getWeightKg());
        assertEquals(0, set.getReps());
        assertNull(set.getPrevWeightKg());
        assertNull(set.getPrevReps());
        assertFalse(set.getIsCompleted());
    }

    @Test
    void addExercisesToWorkout_WhenDoneInPast_CopiesSetsAndSetsPrevStats() {
        when(workoutRepository.findById(100L)).thenReturn(Optional.of(currentWorkout));
        when(exerciseRepository.findById(10L)).thenReturn(Optional.of(exercise));

        // Setup past exercise with 2 sets
        WorkoutExercise pastExercise = new WorkoutExercise();
        pastExercise.setId(50L);
        pastExercise.setRestTimerSeconds(75);

        WorkoutSet pastSet1 = new WorkoutSet();
        pastSet1.setId(501L);
        pastSet1.setSetNumber(1);
        pastSet1.setWeightKg(new BigDecimal("80.00"));
        pastSet1.setReps(10);
        pastSet1.setIsCompleted(true);
        pastExercise.getSets().add(pastSet1);

        WorkoutSet pastSet2 = new WorkoutSet();
        pastSet2.setId(502L);
        pastSet2.setSetNumber(2);
        pastSet2.setWeightKg(new BigDecimal("82.50"));
        pastSet2.setReps(8);
        pastSet2.setIsCompleted(true);
        pastExercise.getSets().add(pastSet2);

        when(workoutExerciseRepository.findPastExercises(eq(1L), eq(10L), eq(100L), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pastExercise));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddExercisesRequest request = new AddExercisesRequest(100L, List.of(10L));
        Workout result = workoutService.addExercisesToWorkout(100L, request);

        assertNotNull(result);
        assertEquals(1, result.getExercises().size());
        WorkoutExercise we = result.getExercises().get(0);
        assertEquals(75, we.getRestTimerSeconds());
        assertEquals(2, we.getSets().size());

        WorkoutSet set1 = we.getSets().get(0);
        assertEquals(1, set1.getSetNumber());
        assertEquals(new BigDecimal("80.00"), set1.getWeightKg());
        assertEquals(10, set1.getReps());
        assertEquals(new BigDecimal("80.00"), set1.getPrevWeightKg());
        assertEquals(10, set1.getPrevReps());
        assertFalse(set1.getIsCompleted());

        WorkoutSet set2 = we.getSets().get(1);
        assertEquals(2, set2.getSetNumber());
        assertEquals(new BigDecimal("82.50"), set2.getWeightKg());
        assertEquals(8, set2.getReps());
        assertEquals(new BigDecimal("82.50"), set2.getPrevWeightKg());
        assertEquals(8, set2.getPrevReps());
        assertFalse(set2.getIsCompleted());
    }

    @Test
    void addSet_WhenPastSetExists_FillsPastWeightAndReps() {
        WorkoutExercise we = new WorkoutExercise();
        we.setId(20L);
        we.setExercise(exercise);
        we.setWorkout(currentWorkout);
        currentWorkout.getExercises().add(we);

        // Add 1 existing set to current exercise
        WorkoutSet existingSet = new WorkoutSet();
        existingSet.setSetNumber(1);
        existingSet.setWeightKg(new BigDecimal("80.00"));
        existingSet.setReps(10);
        existingSet.setPrevWeightKg(new BigDecimal("80.00"));
        existingSet.setPrevReps(10);
        we.getSets().add(existingSet);

        // Setup past exercise which had a 2nd set
        WorkoutExercise pastExercise = new WorkoutExercise();
        WorkoutSet pastSet2 = new WorkoutSet();
        pastSet2.setSetNumber(2);
        pastSet2.setWeightKg(new BigDecimal("85.00"));
        pastSet2.setReps(6);
        pastExercise.getSets().add(pastSet2);

        when(workoutRepository.findById(100L)).thenReturn(Optional.of(currentWorkout));
        when(workoutExerciseRepository.findPastExercises(eq(1L), eq(10L), eq(100L), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(pastExercise));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddSetRequest request = new AddSetRequest(100L, 10L);
        Workout result = workoutService.addSet(request);

        assertNotNull(result);
        assertEquals(2, we.getSets().size());
        WorkoutSet newSet = we.getSets().get(1);
        assertEquals(2, newSet.getSetNumber());
        assertEquals(new BigDecimal("85.00"), newSet.getWeightKg());
        assertEquals(6, newSet.getReps());
        assertEquals(new BigDecimal("85.00"), newSet.getPrevWeightKg());
        assertEquals(6, newSet.getPrevReps());
        assertFalse(newSet.getIsCompleted());
    }

    @Test
    void updateExerciseNotes_UpdatesNotesAndSaves() {
        WorkoutExercise we = new WorkoutExercise();
        we.setId(50L);
        we.setNotes("Old note");

        when(workoutExerciseRepository.findById(50L)).thenReturn(Optional.of(we));
        when(workoutExerciseRepository.save(any(WorkoutExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutExercise updated = workoutService.updateExerciseNotes(50L, "New note");

        assertNotNull(updated);
        assertEquals("New note", updated.getNotes());
        verify(workoutExerciseRepository).save(we);
    }

    @Test
    void completeWorkout_SetsStatusCompletedAndCalculatesDuration() {
        Workout workout = new Workout();
        workout.setId(200L);
        workout.setStartTime(OffsetDateTime.now().minusSeconds(120));
        workout.setStatus("IN_PROGRESS");

        when(workoutRepository.findById(200L)).thenReturn(Optional.of(workout));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        Workout completed = workoutService.completeWorkout(200L);

        assertNotNull(completed);
        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getEndTime());
        assertTrue(completed.getDurationSeconds() >= 119);
        verify(workoutRepository).save(workout);
    }

    @Test
    void save_WhenFirstWorkoutForUser_AssignsWorkoutNumber1AndTrening1() {
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(0);
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        Workout workout = new Workout();
        Workout saved = workoutService.save(workout);

        assertNotNull(saved);
        assertEquals(1, saved.getWorkoutNumber());
        assertEquals("Trening #1", saved.getTitle());
        assertEquals(user, saved.getUser());
    }

    @Test
    void save_WhenSecondWorkoutForUser_AssignsWorkoutNumber2AndTrening2() {
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(1);
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        Workout workout = new Workout();
        Workout saved = workoutService.save(workout);

        assertNotNull(saved);
        assertEquals(2, saved.getWorkoutNumber());
        assertEquals("Trening #2", saved.getTitle());
    }

    @Test
    void save_WithCustomTitle_PreservesCustomTitleAndSetsWorkoutNumber() {
        when(workoutRepository.findMaxWorkoutNumberByUserId(1L)).thenReturn(3);
        when(workoutRepository.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        Workout workout = new Workout();
        workout.setTitle("Push Day");
        Workout saved = workoutService.save(workout);

        assertNotNull(saved);
        assertEquals(4, saved.getWorkoutNumber());
        assertEquals("Push Day", saved.getTitle());
    }

    @Test
    void deleteWorkout_WhenExistsAndBelongsToUser_DeletesAndReturnsTrue() {
        when(workoutRepository.findById(100L)).thenReturn(Optional.of(currentWorkout));

        boolean result = workoutService.deleteWorkout(100L);

        assertTrue(result);
        verify(workoutRepository).delete(currentWorkout);
    }

    @Test
    void deleteWorkout_WhenNotFound_ThrowsNotFoundException() {
        when(workoutRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                workoutService.deleteWorkout(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(workoutRepository, never()).delete(any());
    }

    @Test
    void deleteWorkout_WhenBelongsToAnotherUser_ThrowsForbiddenException() {
        FitnessUser anotherUser = new FitnessUser();
        anotherUser.setId(2L);
        Workout anotherUserWorkout = new Workout();
        anotherUserWorkout.setId(200L);
        anotherUserWorkout.setUser(anotherUser);

        when(workoutRepository.findById(200L)).thenReturn(Optional.of(anotherUserWorkout));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                workoutService.deleteWorkout(200L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(workoutRepository, never()).delete(any());
    }

    @Test
    void deleteWorkout_WhenIdIsNull_ThrowsBadRequestException() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                workoutService.deleteWorkout(null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(workoutRepository, never()).delete(any());
    }

    @Test
    void getAllWorkouts_ReturnsSummariesForCurrentUser() {
        WorkoutSummaryDto dto = WorkoutSummaryDto.builder()
                .id(100L)
                .workoutNumber(1)
                .userId(1L)
                .title("Trening #1")
                .build();
        when(workoutRepository.findSummariesByUserId(1L)).thenReturn(List.of(dto));

        List<WorkoutSummaryDto> results = workoutService.getAllWorkouts();

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).getId());
        assertEquals("Trening #1", results.get(0).getTitle());
        verify(workoutRepository).findSummariesByUserId(1L);
    }

    @Test
    void getWorkoutsByUserId_WhenSameUser_ReturnsSummaries() {
        WorkoutSummaryDto dto = WorkoutSummaryDto.builder()
                .id(100L)
                .workoutNumber(1)
                .userId(1L)
                .title("Trening #1")
                .build();
        when(workoutRepository.findSummariesByUserId(1L)).thenReturn(List.of(dto));

        List<WorkoutSummaryDto> results = workoutService.getWorkoutsByUserId(1L);

        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).getId());
        verify(workoutRepository).findSummariesByUserId(1L);
    }

    @Test
    void getWorkoutsByUserId_WhenDifferentUser_ThrowsForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                workoutService.getWorkoutsByUserId(2L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(workoutRepository, never()).findSummariesByUserId(any());
    }
}
