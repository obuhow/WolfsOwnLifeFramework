CREATE TABLE IF NOT EXISTS project (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    life_area_id BIGINT NOT NULL REFERENCES life_area(id) ON DELETE RESTRICT,
    parent_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    total_plan_hours NUMERIC(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_user ON project (user_id);
CREATE INDEX IF NOT EXISTS idx_project_life_area ON project (life_area_id);
CREATE INDEX IF NOT EXISTS idx_project_parent ON project (parent_id);
CREATE INDEX IF NOT EXISTS idx_project_user_life_area ON project (user_id, life_area_id);