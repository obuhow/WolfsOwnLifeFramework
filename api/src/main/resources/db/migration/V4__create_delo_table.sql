CREATE TABLE IF NOT EXISTS delo (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'SELF',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (execution_mode IN ('SELF', 'DELEGATABLE', 'AUTOMATABLE'))
);

CREATE TABLE IF NOT EXISTS delo_project (
    delo_id BIGINT NOT NULL REFERENCES delo(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (delo_id, project_id)
);

-- Constraint: at most one primary project per delo
CREATE UNIQUE INDEX IF NOT EXISTS uq_delo_primary_project
    ON delo_project (delo_id)
    WHERE is_primary = TRUE;

CREATE INDEX IF NOT EXISTS idx_delo_user ON delo (user_id);
CREATE INDEX IF NOT EXISTS idx_delo_project ON delo_project (project_id);