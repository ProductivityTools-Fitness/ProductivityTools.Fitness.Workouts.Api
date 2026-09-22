package top.productivitytools.fitness.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.productivitytools.fitness.api.dto.external.ExternalSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.services.ExerciseDbService;

import java.util.List;

@RestController
@RequestMapping({"/api/exercisedb", "/api/exercise/external"})
@RequiredArgsConstructor
public class ExerciseDbController {

    private final ExerciseDbService exerciseDbService;

    @GetMapping("/search")
    public List<ExternalSearchResultDto> searchExercises(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String bodyCategory,
            @RequestParam(required = false) String equipmentCategory,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return exerciseDbService.searchExercises(name, bodyCategory, equipmentCategory, limit);
    }

    @PostMapping("/import/{externalExerciseId}")
    public Exercise importExercise(@PathVariable String externalExerciseId) {
        return exerciseDbService.importExercise(externalExerciseId);
    }
}
