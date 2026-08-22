CREATE TABLE note_attachment (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL UNIQUE REFERENCES note(id) ON DELETE CASCADE,
    audio_ref VARCHAR(1000) NOT NULL,
    content_type VARCHAR(200),
    original_filename VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_note_attachment_note ON note_attachment(note_id);
