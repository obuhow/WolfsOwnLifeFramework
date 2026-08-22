ALTER TABLE sync_import_preview ADD COLUMN result_json TEXT;
ALTER TABLE sync_import_preview ADD COLUMN applied_at TIMESTAMP;

CREATE INDEX ix_sync_preview_checksum ON sync_import_preview(checksum);
