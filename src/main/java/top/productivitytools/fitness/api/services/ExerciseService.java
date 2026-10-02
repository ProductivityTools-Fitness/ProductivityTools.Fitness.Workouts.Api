package top.productivitytools.fitness.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.dto.responses.ExerciseHistoryEntryDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.ExerciseImage;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.entities.Workout;
import top.productivitytools.fitness.api.entities.WorkoutExercise;
import top.productivitytools.fitness.api.entities.WorkoutSet;
import top.productivitytools.fitness.api.repositories.ExerciseImageRepository;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.repositories.WorkoutExerciseRepository;
import top.productivitytools.fitness.api.security.UserContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseImageRepository exerciseImageRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;

    public List<Exercise> getExerciseList() {
        FitnessUser user = UserContext.getCurrentUser();
        if (user != null && user.getId() != null) {
            return exerciseRepository.findAvailableExercisesForUser(user.getId());
        }
        return exerciseRepository.findAllByOrderByNameAsc();
    }

    public List<Exercise> getAvailableExercises(Long userId) {
        if (userId == null) {
            return exerciseRepository.findByIsSystemTrueOrderByNameAsc();
        }
        return exerciseRepository.findAvailableExercisesForUser(userId);
    }

    public Optional<Exercise> getExerciseById(Long id) {
        return exerciseRepository.findById(id);
    }

    /**
     * Local copy of the animation, stored when the exercise was imported from the catalogue.
     * Empty for hand-made exercises and for the handful of catalogue entries that have no GIF.
     */
    public Optional<ExerciseImage> getExerciseImage(Long id) {
        return exerciseRepository.findById(id)
                .flatMap(exerciseImageRepository::findByExercise);
    }

    public Exercise updateExerciseSettings(Long id, Boolean wakeLockSentinel) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exercise not found with id: " + id));
        if (wakeLockSentinel != null) {
            exercise.setWakeLockSentinel(wakeLockSentinel);
        }
        return exerciseRepository.save(exercise);
    }

    /**
     * Past performances of an exercise by the current user, newest first, limited to the most
     * recent {@code limit} workouts. Workouts in which no set was logged are skipped.
     */
    @Transactional(readOnly = true)
    public List<ExerciseHistoryEntryDto> getExerciseHistory(Long exerciseId, int limit) {
        FitnessUser user = UserContext.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        if (!exerciseRepository.existsById(exerciseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + exerciseId);
        }
        int pageSize = Math.max(1, Math.min(limit, 200));
        return workoutExerciseRepository
                .findHistoryForExercise(user.getId(), exerciseId, PageRequest.of(0, pageSize))
                .stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    private ExerciseHistoryEntryDto toHistoryEntry(WorkoutExercise we) {
        Workout workout = we.getWorkout();
        List<ExerciseHistoryEntryDto.ExerciseHistorySetDto> sets = we.getSets().stream()
                .sorted(Comparator.comparing(WorkoutSet::getSetNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new ExerciseHistoryEntryDto.ExerciseHistorySetDto(
                        s.getSetNumber(),
                        s.getWeightKg(),
                        s.getReps(),
                        s.getDurationSeconds(),
                        s.getDistanceMeters(),
                        s.getIsCompleted()))
                .toList();
        return new ExerciseHistoryEntryDto(
                workout.getId(),
                workout.getWorkoutNumber(),
                workout.getTitle(),
                workout.getStartTime(),
                workout.getStatus(),
                sets);
    }
}


