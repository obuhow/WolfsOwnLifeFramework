ALTER TABLE "user" ADD COLUMN IF NOT EXISTS time_capture_mode VARCHAR(20) NOT NULL DEFAULT 'PARALLEL_SLOTS';
ALTER TABLE "user" ADD CONSTRAINT chk_user_time_capture_mode CHECK (time_capture_mode IN ('PARALLEL_SLOTS', 'PRIMARY_FOCUS'));

CREATE TABLE IF NOT EXISTS focus_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    delo_id BIGINT NOT NULL REFERENCES delo(id) ON DELETE RESTRICT,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_focus_open_user ON focus_session(user_id) WHERE ended_at IS NULL;

CREATE TABLE IF NOT EXISTS focus_distraction (
    id BIGSERIAL PRIMARY KEY,
    focus_session_id BIGINT NOT NULL REFERENCES focus_session(id) ON DELETE CASCADE,
    delo_id BIGINT REFERENCES delo(id) ON DELETE SET NULL,
    text VARCHAR(500),
    at TIMESTAMP NOT NULL,
    minutes INT
);
CREATE INDEX IF NOT EXISTS idx_focus_distraction_session ON focus_distraction(focus_session_id, at);

