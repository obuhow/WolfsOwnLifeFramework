-- Release 1.5 ticket 01: durable chat sessions and message history.
-- V43 is already used by release 1.4 (supporting_delo_flag); this is the next migration.
CREATE TABLE chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    CONSTRAINT ck_chat_message_content_not_blank CHECK (length(btrim(content)) > 0)
);

CREATE INDEX idx_chat_session_user_updated
    ON chat_session (user_id, updated_at DESC, id DESC);

CREATE INDEX idx_chat_message_session_created
    ON chat_message (session_id, created_at ASC, id ASC);
