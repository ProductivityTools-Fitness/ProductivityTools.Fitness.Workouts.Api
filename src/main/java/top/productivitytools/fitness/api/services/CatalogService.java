package top.productivitytools.fitness.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.dto.external.ExerciseDbItem;
import top.productivitytools.fitness.api.dto.external.ExternalSearchResultDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.repositories.ExerciseDbRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseDbService {

    private final ExerciseDbRepository exerciseDbRepository;
    private final ExerciseDbClient exerciseDbClient;

    public List<ExternalSearchResultDto> searchExercises(String name, String bodyCategory, String equipmentCategory, Integer limit) {
        List<ExerciseDbItem> externalItems = exerciseDbClient.searchExercises(name, bodyCategory, equipmentCategory, limit);
        if (externalItems.isEmpty()) {
            return List.of();
        }

        List<String> externalIds = externalItems.stream()
                .map(ExerciseDbItem::exerciseId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Map<String, Long> existingMap = exerciseDbRepository.findByExternalExerciseIdIn(externalIds)
                .stream()
                .collect(Collectors.toMap(
                        Exercise::getExternalExerciseId,
                        Exercise::getId,
                        (existing, replacement) -> existing
                ));

        return externalItems.stream().map(item -> new ExternalSearchResultDto(
                item.exerciseId(),
                item.name(),
                item.gifUrl(),
                (item.bodyParts() != null && !item.bodyParts().isEmpty()) ? item.bodyParts().get(0) : null,
                (item.equipments() != null && !item.equipments().isEmpty()) ? item.equipments().get(0) : null,
                (item.targetMuscles() != null && !item.targetMuscles().isEmpty()) ? item.targetMuscles().get(0) : null,
                item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of(),
                item.instructions() != null ? item.instructions() : List.of(),
                existingMap.containsKey(item.exerciseId()),
                existingMap.get(item.exerciseId())
        )).toList();
    }

    @Transactional
    public Exercise importExercise(String externalExerciseId) {
        if (externalExerciseId == null || externalExerciseId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External exercise ID must not be empty");
        }

        Optional<Exercise> existing = exerciseDbRepository.findByExternalExerciseId(externalExerciseId);
        if (existing.isPresent()) {
            return existing.get();
        }

        ExerciseDbItem item = exerciseDbClient.getExerciseById(externalExerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found in ExerciseDB with id: " + externalExerciseId));

        Exercise exercise = new Exercise();
        exercise.setExternalExerciseId(item.exerciseId());
        exercise.setName(item.name());
        exercise.setGifUrl(item.gifUrl());
        exercise.setBodyCategory((item.bodyParts() != null && !item.bodyParts().isEmpty()) ? item.bodyParts().get(0) : null);
        exercise.setEquipmentCategory((item.equipments() != null && !item.equipments().isEmpty()) ? item.equipments().get(0) : null);
        exercise.setTargetMuscle((item.targetMuscles() != null && !item.targetMuscles().isEmpty()) ? item.targetMuscles().get(0) : null);
        exercise.setSecondaryMuscles(item.secondaryMuscles() != null ? item.secondaryMuscles() : List.of());
        exercise.setInstructions(item.instructions() != null ? item.instructions() : List.of());
        exercise.setIsSystem(true);

        return exerciseDbRepository.save(exercise);
    }
}
