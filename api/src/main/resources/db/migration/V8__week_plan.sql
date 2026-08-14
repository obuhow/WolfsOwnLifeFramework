-- План на неделю: плановые часы на Проект × ISO-неделя (Гантт)
CREATE TABLE week_plan (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    iso_year INT NOT NULL,
    iso_week INT NOT NULL,
    plan_hours NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_week_plan_user_project_week UNIQUE (user_id, project_id, iso_year, iso_week),
    CONSTRAINT chk_week_plan_hours_nonneg CHECK (plan_hours >= 0),
    CONSTRAINT chk_week_plan_iso_week CHECK (iso_week >= 1 AND iso_week <= 53)
);

CREATE INDEX idx_week_plan_user ON week_plan (user_id);
CREATE INDEX idx_week_plan_user_week ON week_plan (user_id, iso_year, iso_week);
