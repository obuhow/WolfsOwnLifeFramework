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

-- Seed admin is created by DataInitializer with a real BCrypt hash (admin/admin).
-- Do not insert a placeholder hash here — BCrypt will reject it at login.