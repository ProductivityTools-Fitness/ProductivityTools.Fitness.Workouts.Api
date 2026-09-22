package top.productivitytools.fitness.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.productivitytools.fitness.api.entities.Exercise;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogExerciseRepository extends JpaRepository<Exercise, Long> {

    Optional<Exercise> findByCatalogExerciseId(String catalogExerciseId);

    List<Exercise> findByCatalogExerciseIdIn(List<String> catalogExerciseIds);
}
