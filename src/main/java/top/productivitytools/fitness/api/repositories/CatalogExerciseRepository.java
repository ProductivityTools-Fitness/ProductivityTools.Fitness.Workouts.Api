package top.productivitytools.fitness.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.productivitytools.fitness.api.entities.Exercise;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseDbRepository extends JpaRepository<Exercise, Long> {

    Optional<Exercise> findByExternalExerciseId(String externalExerciseId);

    List<Exercise> findByExternalExerciseIdIn(List<String> externalExerciseIds);
}
