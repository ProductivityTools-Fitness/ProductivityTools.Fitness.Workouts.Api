package top.productivitytools.fitness.api.dto.hevy;

import java.util.List;

public record HevyImportResponse(
        int totalFetched,
        int workoutsImported,
        int workoutsSkipped,
        int exercisesCreated,
        String message,
        List<String> importedTitles
) {}
