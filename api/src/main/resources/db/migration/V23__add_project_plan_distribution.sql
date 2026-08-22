ALTER TABLE project
    ADD COLUMN IF NOT EXISTS plan_distribution VARCHAR(20) NOT NULL DEFAULT 'NONE';

ALTER TABLE project
    ADD CONSTRAINT chk_project_plan_distribution
    CHECK (plan_distribution IN ('NONE', 'EVEN_ALL_DAYS', 'EVEN_WEEKDAYS'));

CREATE INDEX IF NOT EXISTS idx_project_user_plan_distribution
    ON project (user_id, plan_distribution);

UPDATE project
SET plan_distribution = 'NONE'
WHERE plan_distribution IS NULL;
