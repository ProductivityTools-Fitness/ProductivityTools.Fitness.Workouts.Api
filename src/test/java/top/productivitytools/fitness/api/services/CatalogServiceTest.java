package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.productivitytools.fitness.api.dto.catalog.CatalogExerciseDto;
import top.productivitytools.fitness.api.dto.catalog.CatalogSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.ExerciseImage;
import top.productivitytools.fitness.api.repositories.ExerciseImageRepository;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private ExerciseImageRepository exerciseImageRepository;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private CatalogService catalogService;

    private static CatalogExerciseDto catalogItem(String id, String name) {
        return new CatalogExerciseDto(
                id, name, "upper arms", "barbell", "biceps",
                List.of("upper arms"), List.of("barbell"), List.of("biceps"),
                List.of("forearms"), List.of("Step one"), name + ".gif", true);
    }

    @Test
    void searchExercises_MarksTheOnesAlreadyInTheLocalDatabase() {
        CatalogExerciseDto curl = catalogItem("fp_barbell_curl", "barbell curl");
        CatalogExerciseDto plank = catalogItem("exdb_plank", "plank");
        when(catalogClient.searchExercises("curl", null, null, 50)).thenReturn(List.of(curl, plank));

        Exercise imported = new Exercise();
        imported.setId(7L);
        imported.setCatalogExerciseId("fp_barbell_curl");
        when(exerciseRepository.findByCatalogExerciseIdIn(List.of("fp_barbell_curl", "exdb_plank")))
                .thenReturn(List.of(imported));

        List<CatalogSearchResultDto> results = catalogService.searchExercises("curl", null, null, 50);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isAlreadyImported());
        assertEquals(7L, results.get(0).localExerciseId());
        assertFalse(results.get(1).isAlreadyImported());
        assertNull(results.get(1).localExerciseId());
    }

    @Test
    void searchExercises_FlattensTheCatalogListsToSingleValues() {
        when(catalogClient.searchExercises(isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of(catalogItem("fp_barbell_curl", "barbell curl")));
        when(exerciseRepository.findByCatalogExerciseIdIn(anyList())).thenReturn(List.of());

        CatalogSearchResultDto result = catalogService.searchExercises(null, null, null, 50).get(0);

        assertEquals("upper arms", result.bodyCategory());
        assertEquals("barbell", result.equipmentCategory());
        assertEquals("biceps", result.targetMuscle());
    }

    @Test
    void importExercise_WhenAlreadyImported_ReturnsTheExistingRowWithoutCallingTheCatalog() {
        Exercise existing = new Exercise();
        existing.setId(7L);
        existing.setCatalogExerciseId("fp_barbell_curl");
        when(exerciseRepository.findByCatalogExerciseId("fp_barbell_curl")).thenReturn(Optional.of(existing));

        Exercise result = catalogService.importExercise("fp_barbell_curl");

        assertSame(existing, result);
        verify(catalogClient, never()).getExercise(anyString());
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void importExercise_CopiesMetadataAndAnimation() {
        byte[] gif = "GIF89a-bytes".getBytes(StandardCharsets.UTF_8);
        when(exerciseRepository.findByCatalogExerciseId("fp_barbell_curl")).thenReturn(Optional.empty());
        when(catalogClient.getExercise("fp_barbell_curl"))
                .thenReturn(Optional.of(catalogItem("fp_barbell_curl", "barbell curl")));
        when(exerciseRepository.findAvailableExercisesByName(isNull(), eq("barbell curl"))).thenReturn(List.of());
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> {
            Exercise e = inv.getArgument(0);
            e.setId(11L);
            return e;
        });
        when(catalogClient.getImage("fp_barbell_curl"))
                .thenReturn(Optional.of(new CatalogClient.CatalogImage(gif, "image/gif")));
        when(exerciseImageRepository.findByExercise(any(Exercise.class))).thenReturn(Optional.empty());

        Exercise saved = catalogService.importExercise("fp_barbell_curl");

        assertEquals("fp_barbell_curl", saved.getCatalogExerciseId());
        assertEquals("barbell curl", saved.getName());
        assertEquals("biceps", saved.getTargetMuscle());
        assertTrue(saved.getIsSystem());
        assertNull(saved.getUser());

        ArgumentCaptorHolder holder = new ArgumentCaptorHolder();
        verify(exerciseImageRepository).save(argThat(image -> {
            holder.image = image;
            return true;
        }));
        assertArrayEquals(gif, holder.image.getImageData());
        assertEquals(gif.length, holder.image.getFileSizeBytes());
        assertEquals("barbell curl.gif", holder.image.getFileName());
    }

    /**
     * The catalogue has a couple of exercises with no animation. That must not abort the import.
     */
    @Test
    void importExercise_WhenCatalogHasNoAnimation_StillImportsTheExercise() {
        when(exerciseRepository.findByCatalogExerciseId("exdb_wall_sit")).thenReturn(Optional.empty());
        when(catalogClient.getExercise("exdb_wall_sit"))
                .thenReturn(Optional.of(catalogItem("exdb_wall_sit", "wall sit")));
        when(exerciseRepository.findAvailableExercisesByName(isNull(), eq("wall sit"))).thenReturn(List.of());
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));
        when(catalogClient.getImage("exdb_wall_sit")).thenReturn(Optional.empty());

        Exercise saved = catalogService.importExercise("exdb_wall_sit");

        assertEquals("exdb_wall_sit", saved.getCatalogExerciseId());
        verify(exerciseImageRepository, never()).save(any());
    }

    /**
     * A system exercise with the same name may already exist (the Hevy preload creates some).
     * The unique index on (user_id, lower(name)) would reject a second row.
     */
    @Test
    void importExercise_WhenANamesakeExistsWithoutCatalogId_AdoptsItInsteadOfInserting() {
        Exercise namesake = new Exercise();
        namesake.setId(3L);
        namesake.setName("barbell curl");
        namesake.setIsSystem(true);

        when(exerciseRepository.findByCatalogExerciseId("fp_barbell_curl")).thenReturn(Optional.empty());
        when(catalogClient.getExercise("fp_barbell_curl"))
                .thenReturn(Optional.of(catalogItem("fp_barbell_curl", "barbell curl")));
        when(exerciseRepository.findAvailableExercisesByName(isNull(), eq("barbell curl")))
                .thenReturn(List.of(namesake));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));
        when(catalogClient.getImage("fp_barbell_curl")).thenReturn(Optional.empty());

        Exercise saved = catalogService.importExercise("fp_barbell_curl");

        assertEquals(3L, saved.getId());
        assertEquals("fp_barbell_curl", saved.getCatalogExerciseId());
    }

    @Test
    void importExercise_WithBlankId_IsRejected() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> catalogService.importExercise("  "));
    }

    @Test
    void importExercise_WhenCatalogDoesNotKnowTheId_IsRejected() {
        when(exerciseRepository.findByCatalogExerciseId("fp_ghost")).thenReturn(Optional.empty());
        when(catalogClient.getExercise("fp_ghost")).thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> catalogService.importExercise("fp_ghost"));
    }

    /** Small holder so the assertions can reach the captured entity. */
    private static final class ArgumentCaptorHolder {
        private ExerciseImage image;
    }
}
