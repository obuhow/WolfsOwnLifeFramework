ALTER TABLE time_entry DROP CONSTRAINT IF EXISTS time_entry_status_check;
ALTER TABLE time_entry ADD CONSTRAINT time_entry_status_check CHECK (status IN ('PLANNED', 'DONE', 'UNKNOWN'));
ALTER TABLE time_entry DROP CONSTRAINT IF EXISTS time_entry_delo_or_ad_hoc_check;
ALTER TABLE time_entry ADD CONSTRAINT time_entry_delo_or_ad_hoc_check CHECK (
    status = 'UNKNOWN' OR
    (delo_id IS NOT NULL AND (ad_hoc_text IS NULL OR ad_hoc_text = '')) OR
    (delo_id IS NULL AND ad_hoc_text IS NOT NULL AND ad_hoc_text <> '')
);

CREATE TABLE IF NOT EXISTS activity_mapping (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    activity_text VARCHAR(500) NOT NULL,
    delo_id BIGINT NOT NULL REFERENCES delo(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_activity_mapping_user_text UNIQUE (user_id, activity_text)
);

CREATE TABLE IF NOT EXISTS xlsx_import_run (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    file_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_cells INT NOT NULL DEFAULT 0,
    mapped INT NOT NULL DEFAULT 0,
    unknown INT NOT NULL DEFAULT 0,
    pending_questions INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_xlsx_import_user_hash ON xlsx_import_run(user_id, file_hash);

CREATE TABLE IF NOT EXISTS xlsx_import_question (
    id BIGSERIAL PRIMARY KEY,
    import_run_id BIGINT NOT NULL REFERENCES xlsx_import_run(id) ON DELETE CASCADE,
    activity_text VARCHAR(500) NOT NULL,
    sheet_name VARCHAR(255) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_xlsx_question_run ON xlsx_import_question(import_run_id, resolved);

CREATE INDEX IF NOT EXISTS idx_time_entry_user_start ON time_entry(user_id, start_at);
