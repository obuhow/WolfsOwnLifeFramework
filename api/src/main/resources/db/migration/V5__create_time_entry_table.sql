CREATE TABLE IF NOT EXISTS time_entry (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    delo_id BIGINT REFERENCES delo(id) ON DELETE SET NULL,
    ad_hoc_text VARCHAR(500),
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status IN ('PLANNED', 'DONE')),
    CHECK (
        (delo_id IS NOT NULL AND (ad_hoc_text IS NULL OR ad_hoc_text = ''))
        OR (delo_id IS NULL AND ad_hoc_text IS NOT NULL AND ad_hoc_text <> '')
    )
);

-- Unique: at most one Запись времени per (user, 15-min start)
CREATE UNIQUE INDEX IF NOT EXISTS uq_time_entry_user_start
    ON time_entry (user_id, start_at);

CREATE INDEX IF NOT EXISTS idx_time_entry_user_range
    ON time_entry (user_id, start_at);

CREATE INDEX IF NOT EXISTS idx_time_entry_delo
    ON time_entry (delo_id);
