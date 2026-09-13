-- ==============================================================================
-- Skrypt czyszczący zawartość tabel z pominięciem tabeli fitness_user
-- oraz resetujący sekwencje identyfikatorów (ID) do wartości początkowej (1).
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

-- Informacja weryfikacyjna:
SELECT 'Tabela fitness_user zachowana: ' || COUNT(*) || ' użytkowników w bazie.' AS status FROM fitness_user;
