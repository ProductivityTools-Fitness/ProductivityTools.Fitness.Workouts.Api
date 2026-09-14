package top.productivitytools.fitness.api.repositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.productivitytools.fitness.api.dto.responses.WorkoutSummaryDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class WorkoutRepositoryIntegrationTest {

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired
    private FitnessUserRepository userRepository;

    @Test
    void testFindSummariesByUserIdQueryExecutes() {
        var user = userRepository.findByEmail("pwujczyk@gmail.com");
        if (user.isPresent()) {
            long start = System.currentTimeMillis();
            List<WorkoutSummaryDto> summaries = workoutRepository.findSummariesByUserId(user.get().getId());
            long duration = System.currentTimeMillis() - start;
            System.out.println(">>> Fetched " + summaries.size() + " workout summaries in " + duration + " ms");
            assertNotNull(summaries);
            assertFalse(summaries.isEmpty());
            WorkoutSummaryDto first = summaries.get(0);
            assertNotNull(first.getId());
            assertNotNull(first.getTitle());
            System.out.println(">>> First workout: " + first.getTitle());
        }
    }
}
