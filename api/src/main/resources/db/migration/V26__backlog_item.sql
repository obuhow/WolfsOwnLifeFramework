CREATE TABLE backlog_item (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    delo_id BIGINT NOT NULL REFERENCES delo(id) ON DELETE CASCADE,
    scope VARCHAR(10) NOT NULL CHECK (scope IN ('WEEK', 'MONTH')),
    period_id VARCHAR(10) NOT NULL,
    planned_hours NUMERIC(6,2),
    position INT NOT NULL DEFAULT 0,
    moved_to_week VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, delo_id, scope, period_id)
);
CREATE INDEX idx_backlog_item_period ON backlog_item(user_id, scope, period_id);
INSERT INTO backlog_item(user_id, delo_id, scope, period_id, position)
SELECT wb.user_id, wbd.delo_id, 'WEEK', CONCAT(wb.iso_year, '-W', LPAD(wb.iso_week::text, 2, '0')),
       ROW_NUMBER() OVER (PARTITION BY wb.id ORDER BY wbd.delo_id) - 1
FROM week_backlog wb JOIN week_backlog_delo wbd ON wbd.backlog_id = wb.id
ON CONFLICT (user_id, delo_id, scope, period_id) DO NOTHING;
DROP TABLE week_backlog_delo;
DROP TABLE week_backlog;
