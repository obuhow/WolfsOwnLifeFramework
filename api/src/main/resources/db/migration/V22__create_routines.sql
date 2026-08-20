CREATE TABLE IF NOT EXISTS routine (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    weekly_hours NUMERIC(8, 2) NOT NULL CHECK (weekly_hours >= 0),
    color VARCHAR(7),
    icon VARCHAR(50),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_routine_user_archived ON routine (user_id, archived, title);

CREATE TABLE IF NOT EXISTS routine_schedule (
    id BIGSERIAL PRIMARY KEY,
    routine_id BIGINT NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT chk_routine_schedule_time_order CHECK (end_time > start_time),
    CONSTRAINT uq_routine_schedule_slot UNIQUE (routine_id, day_of_week, start_time, end_time)
);

CREATE TABLE IF NOT EXISTS routine_goal (
    routine_id BIGINT NOT NULL REFERENCES routine(id) ON DELETE CASCADE,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    PRIMARY KEY (routine_id, goal_id)
);

ALTER TABLE synergy ADD COLUMN IF NOT EXISTS routine_id BIGINT REFERENCES routine(id) ON DELETE CASCADE;
ALTER TABLE synergy DROP CONSTRAINT IF EXISTS synergy_check;
ALTER TABLE synergy ADD CONSTRAINT chk_synergy_one_target
    CHECK ((project_id IS NOT NULL AND idea_id IS NULL AND routine_id IS NULL)
        OR (project_id IS NULL AND idea_id IS NOT NULL AND routine_id IS NULL)
        OR (project_id IS NULL AND idea_id IS NULL AND routine_id IS NOT NULL));
CREATE UNIQUE INDEX IF NOT EXISTS uq_synergy_routine_sphere ON synergy (routine_id, sphere_id)
    WHERE routine_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_synergy_routine ON synergy (routine_id);
