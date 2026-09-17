-- ==============================================================================
-- Skrypt czyszczący zawartość tabel z pominięciem tabeli fitness_user
-- oraz resetujący sekwencje identyfikatorów (ID) do wartości początkowej (1).
--
-- Połączenie z bazą (przy uruchomionym cloud-sql-proxy na porcie 5432):
--   Host:     localhost
--   Port:     5432
--   Database: ptfitness-workouts-api
--   User:     fitness (hasło: Jamnik1!) lub postgres (hasło: Pawel123)
-- ==============================================================================

-- 1. Usunięcie wszystkich danych z tabel w odpowiedniej kolejności kaskadowej
--    oraz automatyczny restart powiązanych sekwencji (RESTART IDENTITY).
TRUNCATE TABLE 
    workout_set,
    workout_exercise,
    workout_template_exercise,
    workout_template,
    workout,
    workout_schedule,
    exercise
RESTART IDENTITY CASCADE;

-- 2. Jawne zresetowanie sekwencji ID (pierwszy nowy rekord otrzyma id = 1).
ALTER SEQUENCE IF EXISTS workout_set_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS workout_exercise_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS workout_template_exercise_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS workout_template_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS workout_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS workout_schedule_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS exercise_id_seq RESTART WITH 1;

-- 3. Weryfikacja po wykonaniu:
SELECT 'fitness_user' AS tabela, COUNT(*) AS liczba_rekordow FROM fitness_user
UNION ALL
SELECT 'exercise' AS tabela, COUNT(*) AS liczba_rekordow FROM exercise
UNION ALL
SELECT 'workout' AS tabela, COUNT(*) AS liczba_rekordow FROM workout
UNION ALL
SELECT 'workout_exercise' AS tabela, COUNT(*) AS liczba_rekordow FROM workout_exercise
UNION ALL
SELECT 'workout_set' AS tabela, COUNT(*) AS liczba_rekordow FROM workout_set;
