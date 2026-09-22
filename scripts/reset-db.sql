-- ==============================================================================
-- Full data reset for ptfitness-workouts
-- ==============================================================================
--
-- Needed when switching the exercise source from ExerciseDB to Fitness.Catalog.Api:
-- the two systems use incompatible identifier schemes (ExerciseDB "EIeI8Vf" versus
-- catalogue "fp_barbell_curl") and no mapping between them exists, so every existing
-- exercise row - and everything referencing it - has to go.
--
-- Difference from clear_database_except_user.sql: that one keeps fitness_user, this one
-- wipes it too and recreates the default account. Use that one for a routine data reset,
-- this one only for the one-off switch to the catalogue.
--
-- THIS DELETES WORKOUT HISTORY. There is no undo.

TRUNCATE TABLE
    exercise_image,
    workout_set,
    workout_exercise,
    workout_template_exercise,
    workout_template,
    workout,
    workout_schedule,
    exercise,
    fitness_user
RESTART IDENTITY CASCADE;

-- Recreate the default user that migration V4 inserted once and will not insert again.
INSERT INTO fitness_user (email, username, default_rest_timer_seconds)
VALUES ('default@fitness.top', 'Default User', 90)
ON CONFLICT (email) DO NOTHING;
