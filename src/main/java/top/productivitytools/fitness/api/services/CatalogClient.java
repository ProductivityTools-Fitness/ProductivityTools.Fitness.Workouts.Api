package top.productivitytools.fitness.api.services;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import top.productivitytools.fitness.api.dto.external.ExerciseDbItem;
import top.productivitytools.fitness.api.dto.external.ExerciseDbSearchResponse;
import top.productivitytools.fitness.api.dto.external.ExerciseDbSingleResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ExerciseDbClient {

    private final RestClient restClient;

    public ExerciseDbClient() {
        this(RestClient.builder()
                .baseUrl("https://oss.exercisedb.dev/api/v1")
                .defaultHeader("User-Agent", "fitness-workouts-api")
                .build());
    }

    public ExerciseDbClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ExerciseDbItem> searchExercises(String name, String bodyParts, String equipments, Integer limit) {
        int targetLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;
        List<ExerciseDbItem> items = new ArrayList<>();
        String cursor = null;

        while (items.size() < targetLimit) {
            int pageSize = Math.min(targetLimit - items.size(), 25);
            final String currentCursor = cursor;

            ExerciseDbSearchResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/exercises");
                        if (name != null && !name.isBlank()) {
                            uriBuilder.queryParam("name", name.trim());
                        }
                        if (bodyParts != null && !bodyParts.isBlank()) {
                            uriBuilder.queryParam("bodyParts", bodyParts.trim());
                        }
                        if (equipments != null && !equipments.isBlank()) {
                            uriBuilder.queryParam("equipments", equipments.trim());
                        }
                        uriBuilder.queryParam("limit", pageSize);
                        if (currentCursor != null && !currentCursor.isBlank()) {
                            uriBuilder.queryParam("after", currentCursor);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(ExerciseDbSearchResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }

            items.addAll(response.data());

            if (items.size() >= targetLimit) {
                break;
            }

            if (response.meta() == null
                    || !Boolean.TRUE.equals(response.meta().hasNextPage())
                    || response.meta().nextCursor() == null
                    || response.meta().nextCursor().isBlank()) {
                break;
            }

            cursor = response.meta().nextCursor();
        }

        if (items.size() > targetLimit) {
            return items.subList(0, targetLimit);
        }
        return items;
    }

    public Optional<ExerciseDbItem> getExerciseById(String exerciseId) {
        if (exerciseId == null || exerciseId.isBlank()) {
            return Optional.empty();
        }
        ExerciseDbSingleResponse response = restClient.get()
                .uri("/exercises/{exerciseId}", exerciseId)
                .retrieve()
                .body(ExerciseDbSingleResponse.class);

        return response != null && response.data() != null ? Optional.of(response.data()) : Optional.empty();
    }
}
