CREATE TABLE IF NOT EXISTS goal (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    priority INTEGER NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_goal_user_priority UNIQUE (user_id, priority),
    CONSTRAINT chk_goal_priority_positive CHECK (priority > 0)
);

CREATE INDEX IF NOT EXISTS idx_goal_user_priority ON goal (user_id, priority);

CREATE TABLE IF NOT EXISTS goal_week_budget (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    iso_year INTEGER NOT NULL,
    iso_week INTEGER NOT NULL,
    hours NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_goal_budget_week UNIQUE (goal_id, iso_year, iso_week),
    CONSTRAINT chk_goal_budget_week CHECK (iso_week BETWEEN 1 AND 53),
    CONSTRAINT chk_goal_budget_hours CHECK (hours >= 0)
);

CREATE INDEX IF NOT EXISTS idx_goal_budget_goal_week ON goal_week_budget (goal_id, iso_year, iso_week);

CREATE TABLE IF NOT EXISTS goal_metric (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    kind VARCHAR(100) NOT NULL,
    value NUMERIC(19, 4) NOT NULL,
    measured_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_goal_metric_goal_at ON goal_metric (goal_id, measured_at DESC);

CREATE TABLE IF NOT EXISTS goal_project (
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    PRIMARY KEY (goal_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_goal_project_project ON goal_project (project_id);
CREATE INDEX IF NOT EXISTS idx_goal_project_goal ON goal_project (goal_id);
