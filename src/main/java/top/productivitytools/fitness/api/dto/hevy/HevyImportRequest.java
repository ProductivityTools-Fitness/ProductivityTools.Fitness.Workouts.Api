package top.productivitytools.fitness.api.dto.hevy;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HevyImportRequest(
        @JsonProperty("access-token")
        String accessToken
) {}
