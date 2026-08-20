CREATE TABLE note (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    delo_id BIGINT REFERENCES delo(id) ON DELETE CASCADE,
    author VARCHAR(20) NOT NULL DEFAULT 'USER',
    body TEXT NOT NULL,
    tags TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_note_exactly_one_parent CHECK (
        (project_id IS NOT NULL AND delo_id IS NULL)
        OR (project_id IS NULL AND delo_id IS NOT NULL)
    ),
    CONSTRAINT ck_note_author CHECK (author IN ('USER', 'AGENT'))
);

CREATE INDEX idx_note_user_created_at ON note (user_id, created_at DESC);
CREATE INDEX idx_note_project ON note (user_id, project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_note_delo ON note (user_id, delo_id) WHERE delo_id IS NOT NULL;
CREATE INDEX idx_note_body_fts ON note USING GIN (to_tsvector('simple', body));
CREATE INDEX idx_note_tags ON note USING GIN (tags);
