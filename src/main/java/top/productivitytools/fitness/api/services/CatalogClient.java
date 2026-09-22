package top.productivitytools.fitness.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import top.productivitytools.fitness.api.dto.catalog.CatalogExerciseDto;

import java.util.List;
import java.util.Optional;

/**
 * HTTP client for Fitness.Catalog.Api.
 *
 * <p>Replaces the former ExerciseDB client. The catalogue returns the complete result list in
 * one response, so there is no cursor pagination here - the limit is simply passed through.
 */
@Component
public class CatalogClient {

    private static final ParameterizedTypeReference<List<CatalogExerciseDto>> LIST_OF_EXERCISES =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    /**
     * Annotated because the class has two constructors: without this marker Spring looks for a
     * no-argument one and fails to start.
     */
    @Autowired
    public CatalogClient(@Value("${catalog.api.url:http://localhost:8085/api}") String baseUrl) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "fitness-workouts-api")
                .build());
    }

    /** Test seam: lets a test inject a {@code RestClient} backed by a mock server. */
    public CatalogClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<CatalogExerciseDto> searchExercises(String name, String bodyPart, String equipment, Integer limit) {
        List<CatalogExerciseDto> result = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/exercises/search");
                    if (name != null && !name.isBlank()) {
                        uriBuilder.queryParam("name", name.trim());
                    }
                    if (bodyPart != null && !bodyPart.isBlank()) {
                        uriBuilder.queryParam("bodyPart", bodyPart.trim());
                    }
                    if (equipment != null && !equipment.isBlank()) {
                        uriBuilder.queryParam("equipment", equipment.trim());
                    }
                    uriBuilder.queryParam("limit", (limit != null && limit > 0) ? limit : 50);
                    return uriBuilder.build();
                })
                .retrieve()
                .body(LIST_OF_EXERCISES);

        return result != null ? result : List.of();
    }

    public Optional<CatalogExerciseDto> getExercise(String catalogExerciseId) {
        if (catalogExerciseId == null || catalogExerciseId.isBlank()) {
            return Optional.empty();
        }

        ResponseEntity<CatalogExerciseDto> response = restClient.get()
                .uri("/exercises/by-exercise-id/{catalogExerciseId}", catalogExerciseId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                    // A missing exercise is an expected outcome, not a failure worth throwing on.
                })
                .toEntity(CatalogExerciseDto.class);

        if (response.getStatusCode().isError()) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.getBody());
    }

    /**
     * Downloads the animation. Returns empty when the catalogue has no image for this
     * exercise - roughly two exercises in the catalogue are in that state, and a missing
     * animation must not block an import.
     */
    public Optional<CatalogImage> getImage(String catalogExerciseId) {
        if (catalogExerciseId == null || catalogExerciseId.isBlank()) {
            return Optional.empty();
        }

        ResponseEntity<byte[]> response = restClient.get()
                .uri("/exercises/{catalogExerciseId}/image", catalogExerciseId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                })
                .toEntity(byte[].class);

        byte[] body = response.getBody();
        if (response.getStatusCode().isError() || body == null || body.length == 0) {
            return Optional.empty();
        }

        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString()
                : "image/gif";

        return Optional.of(new CatalogImage(body, contentType));
    }

    /** Raw animation bytes together with the content type reported by the catalogue. */
    public record CatalogImage(byte[] data, String contentType) {
    }
}
