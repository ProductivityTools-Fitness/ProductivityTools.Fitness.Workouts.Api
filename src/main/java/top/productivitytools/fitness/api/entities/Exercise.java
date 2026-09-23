package top.productivitytools.fitness.api.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exercise")
@Getter
@Setter
@NoArgsConstructor
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Business key of the exercise in Fitness.Catalog.Api, for example {@code fp_barbell_curl}.
     * Null for exercises created by a user by hand.
     */
    @Column(name = "catalog_exercise_id", length = 150)
    private String catalogExerciseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private FitnessUser user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "gif_url", length = 500)
    private String gifUrl;

    /**
     * Set when an animation was copied from the catalogue, so clients know that
     * {@code GET /api/exercise/{id}/image} will return something.
     */
    @Column(name = "image_file_name", length = 255)
    private String imageFileName;

    @Column(name = "equipment_category", length = 50)
    private String equipmentCategory;

    @Column(name = "body_category", length = 50)
    private String bodyCategory;

    @Column(name = "target_muscle", length = 100)
    private String targetMuscle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_muscles", columnDefinition = "jsonb")
    private List<String> secondaryMuscles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instructions", columnDefinition = "jsonb")
    private List<String> instructions = new ArrayList<>();

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    /**
     * How a set of this exercise is measured. Defaults to weight x reps, which is what
     * every exercise created before V11 was implicitly assumed to be.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", nullable = false, length = 20)
    private TrackingType trackingType = TrackingType.WEIGHT_REPS;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public boolean isCustom() {
        return Boolean.FALSE.equals(this.isSystem);
    }
}
