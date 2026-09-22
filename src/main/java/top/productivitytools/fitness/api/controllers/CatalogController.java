package top.productivitytools.fitness.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.productivitytools.fitness.api.dto.catalog.CatalogSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.services.CatalogService;

import java.util.List;

/**
 * Browsing and importing the shared exercise catalogue served by Fitness.Catalog.Api.
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/search")
    public List<CatalogSearchResultDto> searchExercises(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String bodyCategory,
            @RequestParam(required = false) String equipmentCategory,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return catalogService.searchExercises(name, bodyCategory, equipmentCategory, limit);
    }

    @PostMapping("/import/{catalogExerciseId}")
    public Exercise importExercise(@PathVariable String catalogExerciseId) {
        return catalogService.importExercise(catalogExerciseId);
    }

    /**
     * Preview of the animation for an exercise that has not been imported yet, streamed
     * through from the catalogue. Imported exercises are served from the local copy by
     * {@code GET /api/exercise/{id}/image} instead.
     */
    @GetMapping("/{catalogExerciseId}/image")
    public ResponseEntity<byte[]> getCatalogImage(@PathVariable String catalogExerciseId) {
        return catalogService.fetchCatalogImage(catalogExerciseId)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.contentType()))
                        .body(image.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
