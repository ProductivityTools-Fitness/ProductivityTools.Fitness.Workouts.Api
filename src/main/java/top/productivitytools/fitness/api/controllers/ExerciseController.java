package top.productivitytools.fitness.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.services.ExerciseService;

import java.util.List;

@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
public class ExerciseController {
    
    private final ExerciseService exerciseService;

    @GetMapping("/list")
    public List<Exercise> getExerciseList() {
        return exerciseService.getExerciseList();
    }

    @GetMapping("/{id}")
    public Exercise getExerciseById(@PathVariable Long id) {
        return exerciseService.getExerciseById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));
    }

    /**
     * Animation stored locally when the exercise was imported from the catalogue.
     * Returns 404 for exercises that have no GIF, which the client should treat as
     * "show a placeholder", not as an error.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getExerciseImage(@PathVariable Long id) {
        return exerciseService.getExerciseImage(id)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.getContentType()))
                        .body(image.getImageData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

