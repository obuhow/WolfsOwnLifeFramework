CREATE TABLE daily_checklist_item (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    delo_id BIGINT REFERENCES delo(id) ON DELETE SET NULL,
    position INT NOT NULL DEFAULT 0,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    done_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_daily_checklist_user_date ON daily_checklist_item(user_id, date, position);
