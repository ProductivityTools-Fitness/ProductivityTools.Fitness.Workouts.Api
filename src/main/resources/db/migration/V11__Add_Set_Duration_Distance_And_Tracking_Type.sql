-- Adds time- and distance-based set tracking, matching what Hevy records.
--
-- Until now a set could only be described as "weight x reps", so a plank imported from
-- Hevy lost its duration and showed up as 0 kg x 0 reps. Hevy also sends an RPE per set,
-- which is deliberately not stored: V6 removed that column on purpose.
ALTER TABLE workout_set ADD COLUMN duration_seconds INTEGER;
ALTER TABLE workout_set ADD COLUMN distance_meters NUMERIC(10, 2);

-- Which of the above columns are meaningful for a given exercise. Without this the UI
-- cannot tell that a plank wants a stopwatch and a bench press wants a barbell, and a
-- newly added set would have no way to inherit the right shape.
--
-- WEIGHT_REPS      - weight x reps (default, the overwhelming majority)
-- REPS_ONLY        - bodyweight reps, e.g. push-ups
-- DURATION         - time only, e.g. plank, wall sit
-- DURATION_WEIGHT  - time under an external load, e.g. weighted plank
-- DISTANCE_DURATION- distance and time, e.g. running, rowing machine
ALTER TABLE exercise ADD COLUMN tracking_type VARCHAR(20) NOT NULL DEFAULT 'WEIGHT_REPS';

-- Guards against a typo in application code silently writing an unknown type.
ALTER TABLE exercise ADD CONSTRAINT chk_exercise_tracking_type
    CHECK (tracking_type IN ('WEIGHT_REPS', 'REPS_ONLY', 'DURATION', 'DURATION_WEIGHT', 'DISTANCE_DURATION'));
