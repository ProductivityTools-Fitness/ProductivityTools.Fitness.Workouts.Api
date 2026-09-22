package top.productivitytools.fitness.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.ExerciseImage;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.repositories.ExerciseImageRepository;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.security.UserContext;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseImageRepository exerciseImageRepository;

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
}


