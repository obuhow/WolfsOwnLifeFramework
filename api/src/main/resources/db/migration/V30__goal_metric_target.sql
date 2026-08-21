ALTER TABLE goal_metric ADD COLUMN IF NOT EXISTS target_value NUMERIC(19,4);
ALTER TABLE goal_metric ADD CONSTRAINT ck_goal_metric_target_nonnegative CHECK (target_value IS NULL OR target_value >= 0);
ALTER TABLE goal_metric ADD CONSTRAINT ck_goal_metric_value_nonnegative CHECK (value >= 0);

INSERT INTO goal_metric (goal_id, kind, value, target_value, measured_at)
SELECT goal_id, kind, value, NULL, measured_at FROM goal_metric WHERE false;
-- The INSERT above is intentionally a no-op compatibility marker for databases
-- that apply migrations from a generated baseline.
