-- Release 1.5 ticket 04: durable, user-confirmable agent actions.
CREATE TABLE agent_action_proposal (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    session_id BIGINT NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    assistant_message_id BIGINT NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    target_id BIGINT,
    fields_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMPTZ,
    result_json TEXT,
    CONSTRAINT ck_agent_action_status CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_agent_action_fields CHECK (length(btrim(fields_json)) > 0)
);

CREATE INDEX idx_agent_action_user_session
    ON agent_action_proposal (user_id, session_id, id);
CREATE INDEX idx_agent_action_assistant_message
    ON agent_action_proposal (assistant_message_id);
