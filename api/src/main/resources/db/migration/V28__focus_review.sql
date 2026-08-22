ALTER TABLE focus_distraction ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP;
ALTER TABLE focus_distraction ADD COLUMN IF NOT EXISTS applied_minutes INT;
CREATE INDEX IF NOT EXISTS idx_focus_distraction_applied ON focus_distraction(focus_session_id, applied_at);

-- Focus review is deliberately optional: NULL means the user kept the switch as a note.

