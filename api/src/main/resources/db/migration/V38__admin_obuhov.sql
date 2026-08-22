-- Release 0.4 ticket 12: named ADMIN account "obuhov" as a working replacement for the
-- generic seed admin/admin. Idempotent: skips insert if the user already exists.
--
-- Password is intentionally NOT a usable literal: it is a random bcrypt hash that does not
-- correspond to any known plaintext. The real, single-use temporary password is issued after
-- this migration runs via POST /api/v1/admin/users/{id}/reset-password (called by the current
-- "admin"), exactly as described in the ticket. Never commit a real password hash for a
-- meaningful plaintext to source control.
INSERT INTO "user" (
    username, password_hash, timezone, night_start, night_end,
    hour_accounting_mode, role, status, account_type, onboarding_completed_at,
    created_at, updated_at
)
SELECT
    'obuhov',
    '$2b$10$0DJDymiPkxQpGRTPcxhUfumuBbnpJB5AJa7reST/AoZidfIFSyLBy',
    'Europe/Moscow', '23:00:00', '07:00:00',
    'PRIMARY_ONLY', 'ADMIN', 'ACTIVE', 'REGULAR', now(),
    now(), now()
WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE username = 'obuhov');
