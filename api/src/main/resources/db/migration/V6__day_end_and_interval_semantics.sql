-- Logical day boundary after midnight + interval constraints for time_entry.
-- Existing rows already have end_at (= start_at + 15m historically); they remain valid intervals.

ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS day_end TIME NOT NULL DEFAULT '02:00:00';

ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS default_sleep_end TIME NOT NULL DEFAULT '09:00:00';

-- end must be after start (interval length >= 15m is enforced in app)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_time_entry_end_after_start'
    ) THEN
        ALTER TABLE time_entry
            ADD CONSTRAINT chk_time_entry_end_after_start CHECK (end_at > start_at);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_time_entry_user_overlap
    ON time_entry (user_id, start_at, end_at);
