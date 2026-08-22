CREATE TABLE agent_run_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    projects_processed INTEGER NOT NULL DEFAULT 0,
    notes_created INTEGER NOT NULL DEFAULT 0,
    error TEXT
);

CREATE INDEX idx_agent_run_log_user_started ON agent_run_log (user_id, started_at DESC);
