ALTER TABLE sync_import_preview
    ADD CONSTRAINT ck_sync_preview_status CHECK (status IN ('VALID', 'INVALID', 'APPLIED'));

ALTER TABLE sync_import_preview
    ADD CONSTRAINT ck_sync_preview_expiry CHECK (expires_at >= created_at);

CREATE INDEX ix_sync_external_id_lookup
    ON sync_external_id(user_id, entity_type, external_id);
