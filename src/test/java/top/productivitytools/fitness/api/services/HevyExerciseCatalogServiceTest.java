package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogItem;
import top.productivitytools.fitness.api.services.hevy.HevyExerciseCatalogService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HevyExerciseCatalogServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    private HevyExerciseCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new HevyExerciseCatalogService(exerciseRepository);
        catalogService.init();
    }

    @Test
    void init_LoadsAll64Exercises() {
        List<HevyExerciseCatalogItem> items = catalogService.getAllCatalogItems();
        assertEquals(64, items.size());
    }

    @Test
    void findMapping_ByEnglishHevyTitle_ReturnsItem() {
        Optional<HevyExerciseCatalogItem> opt = catalogService.findMapping("Bench Press (Barbell)");
        assertTrue(opt.isPresent());
        assertEquals("EIeI8Vf", opt.get().externalExerciseId());
        assertEquals("barbell bench press", opt.get().name());
        assertEquals("barbell", opt.get().equipmentCategory());
        assertEquals("chest", opt.get().bodyCategory());
    }

    @Test
    void findMapping_ByPolishTitle_ReturnsItem() {
        Optional<HevyExerciseCatalogItem> opt = catalogService.findMapping("Wyciskanie leżąc (sztanga)");
        assertTrue(opt.isPresent());
        assertEquals("EIeI8Vf", opt.get().externalExerciseId());
        assertEquals("barbell bench press", opt.get().name());
    }

    @Test
    void findMapping_CaseInsensitive_ReturnsItem() {
        Optional<HevyExerciseCatalogItem> opt = catalogService.findMapping("bench press (barbell)");
        assertTrue(opt.isPresent());
        assertEquals("barbell bench press", opt.get().name());
    }

    @Test
    void findMapping_OverheadPressBarbell_MapsToBarbellStandingBradfordPress() {
        Optional<HevyExerciseCatalogItem> opt = catalogService.findMapping("Overhead Press (Barbell)");
        assertTrue(opt.isPresent());
        assertEquals("dCPESfR", opt.get().externalExerciseId());
        assertEquals("barbell standing bradford press", opt.get().name());
        assertEquals("barbell", opt.get().equipmentCategory());
        assertEquals("shoulders", opt.get().bodyCategory());
    }

    @Test
    void findMapping_OverheadPressDumbbell_MapsToDumbbellStandingOverheadPress() {
        Optional<HevyExerciseCatalogItem> opt = catalogService.findMapping("Overhead Press (Dumbbell)");
        assertTrue(opt.isPresent());
        assertEquals("A6wtbuL", opt.get().externalExerciseId());
        assertEquals("dumbbell standing overhead press", opt.get().name());
        assertEquals("dumbbell", opt.get().equipmentCategory());
        assertEquals("shoulders", opt.get().bodyCategory());
    }

    @Test
    void findMapping_UnmappedExercises_HaveNullExternalId() {
        Optional<HevyExerciseCatalogItem> rowingOpt = catalogService.findMapping("Rowing Machine");
        assertTrue(rowingOpt.isPresent());
        assertNull(rowingOpt.get().externalExerciseId());
        assertEquals("Rowing Machine", rowingOpt.get().name());

        Optional<HevyExerciseCatalogItem> sledOpt = catalogService.findMapping("Sled Push");
        assertTrue(sledOpt.isPresent());
        assertNull(sledOpt.get().externalExerciseId());
        assertEquals("Sled Push", sledOpt.get().name());
    }

    @Test
    void preloadAllExercises_WhenDatabaseEmpty_SavesAll64Exercises() {
        when(exerciseRepository.findByExternalExerciseId(anyString())).thenReturn(Optional.empty());
        when(exerciseRepository.findAvailableExercisesByName(isNull(), anyString())).thenReturn(List.of());
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        int created = catalogService.preloadAllExercises();

        assertEquals(64, created);
        verify(exerciseRepository, times(64)).save(argThat(ex -> {
            assertTrue(ex.getIsSystem());
            assertNull(ex.getUser());
            assertNotNull(ex.getName());
            return true;
        }));
    }

    @Test
    void preloadAllExercises_WhenAlreadyExists_DoesNotDuplicate() {
        Exercise existingBench = new Exercise();
        existingBench.setId(1L);
        existingBench.setName("barbell bench press");
        existingBench.setExternalExerciseId("EIeI8Vf");
        existingBench.setIsSystem(true);

        when(exerciseRepository.findByExternalExerciseId("EIeI8Vf")).thenReturn(Optional.of(existingBench));
        when(exerciseRepository.findByExternalExerciseId(argThat(id -> !"EIeI8Vf".equals(id)))).thenReturn(Optional.empty());
        when(exerciseRepository.findAvailableExercisesByName(isNull(), anyString())).thenReturn(List.of());
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        int created = catalogService.preloadAllExercises();

        // 63 saved because 1 already existed
        assertEquals(63, created);
        verify(exerciseRepository, times(63)).save(any(Exercise.class));
    }
}
