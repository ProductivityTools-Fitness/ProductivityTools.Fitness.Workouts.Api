package top.productivitytools.fitness.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseDbMeta(
    Integer total,
    Boolean hasNextPage,
    Boolean hasPreviousPage,
    String nextCursor,
    String previousCursor
) {}
