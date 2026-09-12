package top.productivitytools.fitness.api.services.hevy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class HevyClient {

    private final RestClient restClient;

    public HevyClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.hevyapp.com")
                .defaultHeader("User-Agent", "ProductivityTools-Fitness-Api")
                .build();
    }

    public String resolveToken(String requestedToken) {
        if (requestedToken != null && !requestedToken.isBlank()) {
            return sanitizeToken(requestedToken);
        }
        return null;
    }

    public List<JsonNode> fetchWorkouts(String accessToken) {
        String token = sanitizeToken(accessToken);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be empty.");
        }

        String username = resolveUsername(token);
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Failed to resolve Hevy username for the provided accessToken. Please verify that the token is valid.");
        }

        int totalCount = getWorkoutCount(token);
        log.info("Fetching Hevy workouts for user: {}, reported total count: {}", username, totalCount);

        int limit = 5;
        int offset = 0;
        List<JsonNode> allWorkouts = new ArrayList<>();

        while (totalCount < 0 || offset < totalCount) {
            int currentOffset = offset;
            JsonNode pageNode = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user_workouts_paged")
                            .queryParam("username", username)
                            .queryParam("limit", limit)
                            .queryParam("offset", currentOffset)
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("x-api-key", "shelobs_hevy_web")
                    .header("Hevy-Platform", "web")
                    .retrieve()
                    .body(JsonNode.class);

            if (pageNode == null || !pageNode.has("workouts") || !pageNode.get("workouts").isArray()) {
                break;
            }

            JsonNode workoutsArr = pageNode.get("workouts");
            if (workoutsArr.isEmpty()) {
                break;
            }

            for (JsonNode w : workoutsArr) {
                allWorkouts.add(w);
            }

            offset += limit;
            if (workoutsArr.size() < limit) {
                break;
            }
        }

        log.info("Successfully fetched {} workouts from Hevy.", allWorkouts.size());
        return allWorkouts;
    }

    private String resolveUsername(String token) {
        try {
            JsonNode res = restClient.get()
                    .uri("/user/account")
                    .header("authorization", "Bearer " + token)
                    .header("x-api-key", "shelobs_hevy_web")
                    .header("Hevy-Platform", "web")
                    .retrieve()
                    .body(JsonNode.class);
            if (res != null && res.hasNonNull("username")) {
                return res.get("username").asText();
            }
        } catch (Exception e) {
            log.error("Could not resolve Hevy username from /user/account: {}", e.getMessage());
        }
        return null;
    }

    private int getWorkoutCount(String token) {
        try {
            JsonNode res = restClient.get()
                    .uri("/workout_count")
                    .header("authorization", "Bearer " + token)
                    .header("x-api-key", "shelobs_hevy_web")
                    .header("Hevy-Platform", "web")
                    .retrieve()
                    .body(JsonNode.class);
            if (res != null && res.hasNonNull("workout_count")) {
                return res.get("workout_count").asInt();
            }
        } catch (Exception e) {
            log.warn("Could not get workout count from Hevy: {}", e.getMessage());
        }
        return -1;
    }

    private String sanitizeToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
