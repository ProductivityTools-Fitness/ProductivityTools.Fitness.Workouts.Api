-- Adds per-exercise Screen Wake Lock setting ("WakeLockSentinel") so the mobile browser
-- keeps the phone screen awake while performing exercises that have this option enabled.
ALTER TABLE exercise ADD COLUMN wake_lock_sentinel BOOLEAN NOT NULL DEFAULT FALSE;

-- Enable by default for time-based exercises (e.g. Plank), where the user is holding a
-- position without touching the screen.
UPDATE exercise
SET wake_lock_sentinel = TRUE
WHERE tracking_type IN ('DURATION', 'DURATION_WEIGHT', 'DISTANCE_DURATION');
