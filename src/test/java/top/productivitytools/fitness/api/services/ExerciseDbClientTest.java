package top.productivitytools.fitness.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.productivitytools.fitness.api.dto.external.ExerciseDbItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExerciseDbClientTest {

    private ExerciseDbClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://oss.exercisedb.dev/api/v1");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new ExerciseDbClient(builder.build());
    }

    @Test
    void searchExercises_WhenDefaultLimit50_FetchesAcrossTwoPages() {
        String page1Json = """
            {
              "success": true,
              "meta": {
                "total": 50,
                "hasNextPage": true,
                "nextCursor": "cursor_page_2"
              },
              "data": [
                {
                  "exerciseId": "ex1",
                  "name": "bench press 1",
                  "gifUrl": "https://example.com/1.gif",
                  "bodyParts": ["chest"],
                  "equipments": ["barbell"],
                  "targetMuscles": ["pectorals"],
                  "secondaryMuscles": ["triceps"],
                  "instructions": ["step 1"]
                }
              ]
            }
            """;

        String page2Json = """
            {
              "success": true,
              "meta": {
                "total": 50,
                "hasNextPage": false,
                "nextCursor": null
              },
              "data": [
                {
                  "exerciseId": "ex2",
                  "name": "bench press 2",
                  "gifUrl": "https://example.com/2.gif",
                  "bodyParts": ["chest"],
                  "equipments": ["barbell"],
                  "targetMuscles": ["pectorals"],
                  "secondaryMuscles": ["triceps"],
                  "instructions": ["step 2"]
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://oss.exercisedb.dev/api/v1/exercises?name=bench&limit=25"))
                .andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://oss.exercisedb.dev/api/v1/exercises?name=bench&limit=25&after=cursor_page_2"))
                .andRespond(withSuccess(page2Json, MediaType.APPLICATION_JSON));

        List<ExerciseDbItem> result = client.searchExercises("bench", null, null, null);

        mockServer.verify();
        assertEquals(2, result.size());
        assertEquals("ex1", result.get(0).exerciseId());
        assertEquals("ex2", result.get(1).exerciseId());
    }

    @Test
    void searchExercises_WhenSinglePageUnderLimit_StopsImmediately() {
        String page1Json = """
            {
              "success": true,
              "meta": {
                "total": 1,
                "hasNextPage": false,
                "nextCursor": null
              },
              "data": [
                {
                  "exerciseId": "ex1",
                  "name": "rare exercise",
                  "gifUrl": null,
                  "bodyParts": ["chest"],
                  "equipments": ["cable"],
                  "targetMuscles": ["pectorals"],
                  "secondaryMuscles": [],
                  "instructions": []
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://oss.exercisedb.dev/api/v1/exercises?name=rare&limit=25"))
                .andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON));

        List<ExerciseDbItem> result = client.searchExercises("rare", null, null, 50);

        mockServer.verify();
        assertEquals(1, result.size());
        assertEquals("ex1", result.get(0).exerciseId());
    }
}
