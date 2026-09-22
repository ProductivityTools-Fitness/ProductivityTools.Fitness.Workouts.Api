package top.productivitytools.fitness.api.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Animation of an exercise, copied from Fitness.Catalog.Api when the exercise is imported.
 *
 * <p>Holding a local copy means that browsing workouts does not depend on the catalogue
 * service being reachable.
 */
@Entity
@Table(name = "exercise_image")
@Getter
@Setter
@NoArgsConstructor
public class ExerciseImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One image per exercise - the column carries a UNIQUE constraint, so storing a new
     * animation has to update the existing row rather than insert a second one.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false, unique = true)
    private Exercise exercise;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType = "image/gif";

    @Column(name = "file_size_bytes", nullable = false)
    private Integer fileSizeBytes;

    /**
     * Deliberately not annotated with {@code @Lob}: on PostgreSQL that maps to a large
     * object OID, while the column is declared BYTEA. A plain byte[] maps correctly.
     */
    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
