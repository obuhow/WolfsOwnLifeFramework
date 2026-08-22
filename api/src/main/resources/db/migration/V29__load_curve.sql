CREATE TABLE load_curve_entry (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    routine_id BIGINT REFERENCES routine(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    hours NUMERIC(8,2) NOT NULL CHECK (hours >= 0),
    CONSTRAINT ck_load_curve_one_owner CHECK ((project_id IS NOT NULL) <> (routine_id IS NOT NULL)),
    CONSTRAINT uq_load_curve_project_week UNIQUE (project_id, week_start),
    CONSTRAINT uq_load_curve_routine_week UNIQUE (routine_id, week_start)
);
CREATE INDEX idx_load_curve_week ON load_curve_entry(week_start);
