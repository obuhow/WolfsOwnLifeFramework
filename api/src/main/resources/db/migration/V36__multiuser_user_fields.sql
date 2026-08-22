-- Release 0.4: multi-user identity fields on "user"
ALTER TABLE "user"
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    ADD COLUMN email VARCHAR(255) NULL,
    ADD COLUMN expires_at TIMESTAMP NULL,
    ADD COLUMN onboarding_completed_at TIMESTAMP NULL,
    ADD COLUMN last_login_at TIMESTAMP NULL;

ALTER TABLE "user"
    ADD CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN')),
    ADD CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    ADD CONSTRAINT ck_user_account_type CHECK (account_type IN ('REGULAR', 'DEMO'));

CREATE UNIQUE INDEX uq_user_email ON "user" (email) WHERE email IS NOT NULL;

CREATE INDEX ix_user_account_type_expires_at ON "user" (account_type, expires_at);

-- Existing admin becomes the first ADMIN (idempotent).
UPDATE "user" SET role = 'ADMIN' WHERE username = 'admin';

-- Existing users must not see the first-run wizard.
UPDATE "user" SET onboarding_completed_at = created_at WHERE onboarding_completed_at IS NULL;
