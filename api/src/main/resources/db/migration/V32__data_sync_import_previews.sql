CREATE TABLE sync_import_preview (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    checksum VARCHAR(128) NOT NULL,
    workbook_data BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL,
    summary_json TEXT NOT NULL,
    errors_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sync_preview_user_checksum UNIQUE (user_id, checksum)
);
CREATE INDEX ix_sync_preview_user ON sync_import_preview(user_id, id);
