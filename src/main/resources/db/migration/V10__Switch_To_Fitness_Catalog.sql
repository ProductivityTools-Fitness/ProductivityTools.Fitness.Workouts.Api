-- Switches the exercise catalogue source from ExerciseDB to Fitness.Catalog.Api.
--
-- The old column held native ExerciseDB identifiers (7 characters, e.g. "EIeI8Vf").
-- The catalogue uses slugs instead ("fp_barbell_curl", "exdb_plank", up to 43 characters),
-- so the column is both renamed and widened. The two id schemes have no overlap and no
-- automatic mapping exists, which is why the data is wiped separately by scripts/reset-db.sql.
ALTER TABLE exercise RENAME COLUMN external_exercise_id TO catalog_exercise_id;
ALTER TABLE exercise ALTER COLUMN catalog_exercise_id TYPE VARCHAR(150);
ALTER INDEX IF EXISTS idx_exercise_external_exercise_id RENAME TO idx_exercise_catalog_exercise_id;

-- Lets a client tell whether an animation is available without probing the image endpoint
-- and handling a 404. Mirrors the same column in Fitness.Catalog.Api.
ALTER TABLE exercise ADD COLUMN image_file_name VARCHAR(255);

-- Animations are copied from the catalogue into this database when an exercise is imported,
-- so that displaying a workout does not depend on Fitness.Catalog.Api being up.
-- BYTEA rather than a large object: the JPA mapping is a plain byte[] and the files are
-- small (largest animation in the catalogue is ~1.2 MB).
CREATE TABLE exercise_image (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL UNIQUE REFERENCES exercise(id) ON DELETE CASCADE,
    file_name VARCHAR(255),
    content_type VARCHAR(100) NOT NULL DEFAULT 'image/gif',
    file_size_bytes INTEGER NOT NULL,
    image_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
