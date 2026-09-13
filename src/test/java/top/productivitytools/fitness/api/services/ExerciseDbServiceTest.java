package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.dto.external.ExerciseDbItem;
import top.productivitytools.fitness.api.dto.external.ExternalSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.repositories.ExerciseDbRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciseDbServiceTest {

    @Mock
    private ExerciseDbRepository exerciseDbRepository;

    @Mock
    private ExerciseDbClient exerciseDbClient;

    @InjectMocks
    private ExerciseDbService exerciseDbService;

    @Test
    void searchExercises_EnrichesWithExistingLocalExercises() {
        ExerciseDbItem item1 = new ExerciseDbItem(
                "ex1", "Bench Press", "https://example.com/1.gif",
                List.of("chest"), List.of("barbell"), List.of("pectorals"), List.of(), List.of("step 1")
        );
        ExerciseDbItem item2 = new ExerciseDbItem(
                "ex2", "Incline Dumbbell Press", "https://example.com/2.gif",
                List.of("chest"), List.of("dumbbell"), List.of("pectorals"), List.of(), List.of("step 1")
        );

        when(exerciseDbClient.searchExercises("press", null, null, 50))
                .thenReturn(List.of(item1, item2));

        Exercise existingExercise = new Exercise();
        existingExercise.setId(99L);
        existingExercise.setExternalExerciseId("ex1");
        existingExercise.setName("Bench Press");

        when(exerciseDbRepository.findByExternalExerciseIdIn(List.of("ex1", "ex2")))
                .thenReturn(List.of(existingExercise));

        List<ExternalSearchResultDto> results = exerciseDbService.searchExercises("press", null, null, 50);

        assertEquals(2, results.size());

        ExternalSearchResultDto r1 = results.get(0);
        assertEquals("ex1", r1.externalExerciseId());
        assertTrue(r1.isAlreadyImported());
        assertEquals(99L, r1.localExerciseId());

        ExternalSearchResultDto r2 = results.get(1);
        assertEquals("ex2", r2.externalExerciseId());
        assertFalse(r2.isAlreadyImported());
        assertNull(r2.localExerciseId());
    }

    @Test
    void searchExercises_WhenExternalEmpty_ReturnsEmptyList() {
        when(exerciseDbClient.searchExercises("xyz", null, null, 50))
                .thenReturn(List.of());

        List<ExternalSearchResultDto> results = exerciseDbService.searchExercises("xyz", null, null, 50);

        assertTrue(results.isEmpty());
        verify(exerciseDbRepository, never()).findByExternalExerciseIdIn(any());
    }

    @Test
    void importExercise_WhenAlreadyImported_ReturnsExisting() {
        Exercise existing = new Exercise();
        existing.setId(42L);
        existing.setExternalExerciseId("ex1");

        when(exerciseDbRepository.findByExternalExerciseId("ex1")).thenReturn(Optional.of(existing));

        Exercise result = exerciseDbService.importExercise("ex1");

        assertSame(existing, result);
        verify(exerciseDbClient, never()).getExerciseById(any());
        verify(exerciseDbRepository, never()).save(any());
    }

    @Test
    void importExercise_WhenNew_FetchesFromClientAndSaves() {
        when(exerciseDbRepository.findByExternalExerciseId("ex2")).thenReturn(Optional.empty());

        ExerciseDbItem item = new ExerciseDbItem(
                "ex2", "Squat", "https://example.com/squat.gif",
                List.of("legs"), List.of("barbell"), List.of("quads"), List.of("glutes"), List.of("step 1")
        );
        when(exerciseDbClient.getExerciseById("ex2")).thenReturn(Optional.of(item));
        when(exerciseDbRepository.save(any(Exercise.class))).thenAnswer(inv -> {
            Exercise e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });

        Exercise result = exerciseDbService.importExercise("ex2");

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("ex2", result.getExternalExerciseId());
        assertEquals("Squat", result.getName());
        assertTrue(result.getIsSystem());
        verify(exerciseDbRepository).save(any(Exercise.class));
    }

    @Test
    void importExercise_WhenBlankId_ThrowsBadRequest() {
        assertThrows(ResponseStatusException.class, () -> exerciseDbService.importExercise("  "));
    }
}
