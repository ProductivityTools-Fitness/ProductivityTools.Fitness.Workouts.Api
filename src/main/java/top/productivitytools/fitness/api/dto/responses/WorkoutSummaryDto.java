package top.productivitytools.fitness.api.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutSummaryDto {
    private Long id;
    private Integer workoutNumber;
    private Long userId;
    private String title;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer durationSeconds;
    private String status;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
