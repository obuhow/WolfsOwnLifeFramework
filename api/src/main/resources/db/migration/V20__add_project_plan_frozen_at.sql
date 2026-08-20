ALTER TABLE project
    ADD COLUMN IF NOT EXISTS plan_frozen_at DATE;

CREATE INDEX IF NOT EXISTS idx_project_plan_frozen_at
    ON project (user_id, plan_frozen_at);

COMMENT ON COLUMN project.plan_frozen_at IS
    'First day of the calendar month when the project plan was frozen';

CREATE OR REPLACE FUNCTION wolf_freeze_project_plans()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE project
       SET plan_frozen_at = date_trunc('month', CURRENT_DATE)::date,
           updated_at = CURRENT_TIMESTAMP
     WHERE plan_frozen_at IS NULL
        OR plan_frozen_at < date_trunc('month', CURRENT_DATE)::date;
END;
$$;

SELECT wolf_freeze_project_plans();

DROP FUNCTION wolf_freeze_project_plans();
