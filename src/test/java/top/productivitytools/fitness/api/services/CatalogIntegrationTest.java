package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.productivitytools.fitness.api.dto.catalog.CatalogSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.ExerciseImage;
import top.productivitytools.fitness.api.repositories.ExerciseImageRepository;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end check of the catalogue integration: real HTTP to Fitness.Catalog.Api and real
 * writes to the database.
 *
 * <p>Disabled unless {@code -Dcatalog.integration=true} is given, because it needs
 * Fitness.Catalog.Api listening on port 8085 and a reachable database. It also inserts rows,
 * so it is not something to run against anything precious.
 *
 * <pre>
 * DB_PASSWORD=... ./gradlew test --tests '*CatalogIntegrationTest' -Dcatalog.integration=true
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "catalog.integration", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogIntegrationTest {

    private static final String CURL = "fp_barbell_curl";

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CatalogClient catalogClient;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExerciseImageRepository exerciseImageRepository;

    @Test
    @Order(1)
    void search_ReachesTheCatalogAndReturnsResults() {
        List<CatalogSearchResultDto> results = catalogService.searchExercises("barbell curl", null, null, 20);

        assertFalse(results.isEmpty(), "the catalogue returned nothing for 'barbell curl'");
        CatalogSearchResultDto curl = results.stream()
                .filter(r -> CURL.equals(r.catalogExerciseId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("fp_barbell_curl missing from the results"));

        assertEquals("barbell curl", curl.name());
        assertNotNull(curl.bodyCategory());
        assertTrue(curl.hasImage());
    }

    @Test
    @Order(2)
    void search_FiltersByBodyPartAndEquipment() {
        List<CatalogSearchResultDto> results =
                catalogService.searchExercises(null, "waist", "body weight", 25);

        assertFalse(results.isEmpty());
        assertTrue(results.size() <= 25, "the limit was not honoured");
        assertTrue(results.stream().allMatch(r -> "waist".equalsIgnoreCase(r.bodyCategory())));
    }

    @Test
    @Order(3)
    void import_CopiesTheExerciseAndItsAnimationIntoTheDatabase() {
        Exercise imported = catalogService.importExercise(CURL);

        assertNotNull(imported.getId());
        assertEquals(CURL, imported.getCatalogExerciseId());
        assertEquals("barbell curl", imported.getName());
        assertTrue(imported.getIsSystem());
        assertNull(imported.getUser());
        assertFalse(imported.getInstructions().isEmpty());

        Exercise reloaded = exerciseRepository.findByCatalogExerciseId(CURL).orElseThrow();
        assertNotNull(reloaded.getImageFileName(), "imageFileName was not set, the client will hide the GIF");

        Optional<ExerciseImage> stored = exerciseImageRepository.findByExercise(reloaded);
        assertTrue(stored.isPresent(), "no animation was stored");

        byte[] fromCatalog = catalogClient.getImage(CURL).orElseThrow().data();
        assertArrayEquals(fromCatalog, stored.get().getImageData(),
                "the stored bytes differ from what the catalogue serves");
        assertEquals(fromCatalog.length, stored.get().getFileSizeBytes());
        assertEquals("image/gif", stored.get().getContentType());
    }

    @Test
    @Order(4)
    void import_IsIdempotent() {
        Exercise first = exerciseRepository.findByCatalogExerciseId(CURL).orElseThrow();
        Exercise again = catalogService.importExercise(CURL);

        assertEquals(first.getId(), again.getId());
        assertEquals(1, exerciseRepository.findByCatalogExerciseIdIn(List.of(CURL)).size());
    }

    @Test
    @Order(5)
    void search_MarksTheImportedExercise() {
        CatalogSearchResultDto curl = catalogService.searchExercises("barbell curl", null, null, 20).stream()
                .filter(r -> CURL.equals(r.catalogExerciseId()))
                .findFirst()
                .orElseThrow();

        assertTrue(curl.isAlreadyImported());
        assertNotNull(curl.localExerciseId());
    }

    /** Two catalogue entries have no GIF; importing them must still work. */
    @Test
    @Order(6)
    void import_WorksForAnExerciseWithoutAnAnimation() {
        Exercise wallSit = catalogService.importExercise("exdb_wall_sit");

        assertNotNull(wallSit.getId());
        assertNull(wallSit.getImageFileName());
        assertTrue(exerciseImageRepository.findByExercise(wallSit).isEmpty());
    }
}
