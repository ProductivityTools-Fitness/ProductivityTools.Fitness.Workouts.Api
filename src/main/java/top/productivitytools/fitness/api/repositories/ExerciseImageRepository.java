package top.productivitytools.fitness.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.ExerciseImage;

import java.util.Optional;

@Repository
public interface ExerciseImageRepository extends JpaRepository<ExerciseImage, Long> {

    /**
     * Takes the entity rather than an id on purpose. {@code Exercise} has both a numeric
     * {@code id} and a business key {@code catalogExerciseId}, so a method named
     * {@code findByExerciseId} would be ambiguous for Spring Data's query derivation.
     */
    Optional<ExerciseImage> findByExercise(Exercise exercise);
}
