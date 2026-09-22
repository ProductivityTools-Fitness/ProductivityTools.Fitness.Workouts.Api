package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.productivitytools.fitness.api.dto.catalog.CatalogExerciseDto;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientTest {

    private static final String BASE_URL = "http://localhost:8085/api";

    private CatalogClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogClient(builder.build());
    }

    @Test
    void searchExercises_PassesEveryCriterionAndParsesTheResponse() {
        String json = """
                [
                  {
                    "exerciseId": "fp_barbell_curl",
                    "name": "barbell curl",
                    "primaryBodyPart": "upper arms",
                    "primaryEquipment": "barbell",
                    "primaryMuscle": "biceps",
                    "bodyParts": ["upper arms"],
                    "equipments": ["barbell"],
                    "targetMuscles": ["biceps"],
                    "secondaryMuscles": ["forearms"],
                    "instructions": ["Step one"],
                    "imageFileName": "barbell-curl.gif",
                    "hasImage": true
                  }
                ]
                """;

        mockServer.expect(requestTo(BASE_URL + "/exercises/search?name=curl&bodyPart=upper%20arms&equipment=barbell&limit=10"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<CatalogExerciseDto> result = client.searchExercises("curl", "upper arms", "barbell", 10);

        mockServer.verify();
        assertEquals(1, result.size());
        CatalogExerciseDto item = result.get(0);
        assertEquals("fp_barbell_curl", item.exerciseId());
        assertEquals("barbell curl", item.name());
        assertEquals(List.of("biceps"), item.targetMuscles());
        assertTrue(item.hasImage());
    }

    @Test
    void searchExercises_OmitsBlankCriteriaAndDefaultsTheLimit() {
        mockServer.expect(requestTo(BASE_URL + "/exercises/search?limit=50"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<CatalogExerciseDto> result = client.searchExercises("  ", null, "", null);

        mockServer.verify();
        assertTrue(result.isEmpty());
    }

    @Test
    void getExercise_WhenCatalogReturns404_ReturnsEmptyInsteadOfThrowing() {
        mockServer.expect(requestTo(BASE_URL + "/exercises/by-exercise-id/fp_nonexistent"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        Optional<CatalogExerciseDto> result = client.getExercise("fp_nonexistent");

        mockServer.verify();
        assertTrue(result.isEmpty());
    }

    @Test
    void getImage_ReturnsBytesAndContentType() {
        byte[] gif = "GIF89a-pretend-this-is-an-animation".getBytes(StandardCharsets.UTF_8);
        mockServer.expect(requestTo(BASE_URL + "/exercises/fp_barbell_curl/image"))
                .andRespond(withSuccess(gif, MediaType.IMAGE_GIF));

        Optional<CatalogClient.CatalogImage> result = client.getImage("fp_barbell_curl");

        mockServer.verify();
        assertTrue(result.isPresent());
        assertArrayEquals(gif, result.get().data());
        assertEquals("image/gif", result.get().contentType());
    }

    @Test
    void getImage_WhenExerciseHasNoAnimation_ReturnsEmpty() {
        mockServer.expect(requestTo(BASE_URL + "/exercises/exdb_wall_sit/image"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        Optional<CatalogClient.CatalogImage> result = client.getImage("exdb_wall_sit");

        mockServer.verify();
        assertTrue(result.isEmpty());
    }

    @Test
    void getImage_WithBlankId_DoesNotCallTheCatalog() {
        assertTrue(client.getImage("  ").isEmpty());
        mockServer.verify();
    }

    /**
     * The class has two constructors - one for Spring, one as a test seam. Without an explicit
     * marker Spring looks for a no-argument constructor and the whole context fails to start,
     * which no test that builds the client by hand would ever notice.
     */
    @Test
    void springCanInstantiateTheClientFromConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("catalog.api.url=http://localhost:9999/api").applyTo(context);
            context.register(CatalogClient.class);
            context.refresh();

            assertNotNull(context.getBean(CatalogClient.class));
        }
    }
}
