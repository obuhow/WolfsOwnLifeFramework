ALTER TABLE project
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS';

ALTER TABLE project
    DROP CONSTRAINT IF EXISTS chk_project_status;

ALTER TABLE project
    ADD CONSTRAINT chk_project_status CHECK (status IN ('IN_PROGRESS', 'ARCHIVED'));

CREATE INDEX IF NOT EXISTS idx_project_user_status ON project (user_id, status);
