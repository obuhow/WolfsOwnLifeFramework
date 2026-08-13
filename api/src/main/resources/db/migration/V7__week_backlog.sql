-- Week backlog tables
CREATE TABLE week_backlog (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    iso_year INT NOT NULL,
    iso_week INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, iso_year, iso_week)
);

CREATE TABLE week_backlog_delo (
    backlog_id BIGINT NOT NULL REFERENCES week_backlog(id) ON DELETE CASCADE,
    delo_id BIGINT NOT NULL REFERENCES delo(id) ON DELETE CASCADE,
    PRIMARY KEY (backlog_id, delo_id)
);

CREATE INDEX idx_week_backlog_user ON week_backlog (user_id);