package top.productivitytools.fitness.api.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import top.productivitytools.fitness.api.entities.Exercise;

import java.util.List;

@Repository
public interface ExerciseRepository extends CatalogExerciseRepository {

    List<Exercise> findAllByOrderByNameAsc();

    List<Exercise> findByIsSystemTrueOrderByNameAsc();

    @Query("SELECT e FROM Exercise e WHERE e.isSystem = true OR (e.user IS NOT NULL AND e.user.id = :userId) ORDER BY e.name ASC")
    List<Exercise> findAvailableExercisesForUser(@Param("userId") Long userId);

    @Query("SELECT e FROM Exercise e WHERE LOWER(e.name) = LOWER(:name) AND (e.isSystem = true OR (e.user IS NOT NULL AND e.user.id = :userId)) ORDER BY e.isSystem DESC")
    List<Exercise> findAvailableExercisesByName(@Param("userId") Long userId, @Param("name") String name);
}
