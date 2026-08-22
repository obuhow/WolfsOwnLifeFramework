CREATE TABLE IF NOT EXISTS idea (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(20) NOT NULL CHECK (category IN ('BUSINESS', 'MUSIC', 'PERSONAL', 'CREEPY')),
    status VARCHAR(20) NOT NULL DEFAULT 'BANK' CHECK (status IN ('BANK', 'IN_WORK', 'ARCHIVED')),
    promoted_project_id BIGINT REFERENCES project(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_idea_user_status ON idea (user_id, status);
CREATE INDEX IF NOT EXISTS idx_idea_user_category ON idea (user_id, category);
CREATE UNIQUE INDEX IF NOT EXISTS uq_idea_promoted_project ON idea (promoted_project_id)
    WHERE promoted_project_id IS NOT NULL;

ALTER TABLE synergy
    ADD CONSTRAINT fk_synergy_idea
    FOREIGN KEY (idea_id) REFERENCES idea(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_idea_promoted_project ON idea (promoted_project_id);
