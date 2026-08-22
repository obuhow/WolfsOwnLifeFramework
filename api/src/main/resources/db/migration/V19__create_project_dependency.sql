CREATE TABLE IF NOT EXISTS project_dependency (
    blocker_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_dependency_pair
    ON project_dependency (blocker_id, blocked_id);

CREATE INDEX IF NOT EXISTS idx_project_dependency_blocked
    ON project_dependency (user_id, blocked_id);

CREATE INDEX IF NOT EXISTS idx_project_dependency_blocker
    ON project_dependency (user_id, blocker_id);

ALTER TABLE project_dependency
    ADD CONSTRAINT chk_project_dependency_distinct
    CHECK (blocker_id <> blocked_id);
