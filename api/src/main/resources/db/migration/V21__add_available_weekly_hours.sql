ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS available_weekly_hours NUMERIC(6, 2) NOT NULL DEFAULT 30.00;

ALTER TABLE "user"
    ADD CONSTRAINT chk_user_available_weekly_hours_nonnegative
    CHECK (available_weekly_hours >= 0);
