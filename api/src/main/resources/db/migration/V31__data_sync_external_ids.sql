CREATE TABLE sync_external_id (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    CONSTRAINT uk_sync_external_id_value UNIQUE (user_id, entity_type, external_id),
    CONSTRAINT uk_sync_external_id_entity UNIQUE (user_id, entity_type, entity_id)
);

CREATE INDEX ix_sync_external_id_user ON sync_external_id(user_id);
