CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Moscow',
    night_start TIME NOT NULL DEFAULT '23:00:00',
    night_end TIME NOT NULL DEFAULT '07:00:00',
    hour_accounting_mode VARCHAR(20) NOT NULL DEFAULT 'PRIMARY_ONLY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed admin user (password: admin)
-- BCrypt hash for "admin" with cost 10
INSERT INTO "user" (username, password_hash, timezone, night_start, night_end, hour_accounting_mode)
VALUES ('admin', '$2a$10$eW8xLnjN8qoF4YqYqYqYqOeW8xLnjN8qoF4YqYqYqYqOeW8xLnjN8q', 'Europe/Moscow', '23:00:00', '07:00:00', 'PRIMARY_ONLY')
ON CONFLICT (username) DO NOTHING;