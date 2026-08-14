-- Simple recurrence rule on Дело: weekdays + optional time window.
-- Horizon is an apply-time parameter, not stored.

ALTER TABLE delo
    ADD COLUMN IF NOT EXISTS recurrence_weekdays VARCHAR(64);

ALTER TABLE delo
    ADD COLUMN IF NOT EXISTS recurrence_window_start TIME;

ALTER TABLE delo
    ADD COLUMN IF NOT EXISTS recurrence_window_end TIME;
