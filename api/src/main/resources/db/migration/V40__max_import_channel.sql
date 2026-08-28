-- Release 0.7, ticket 04: Max import channel
--
-- 1) Promote the per-user daily import-bot rate limit to a SHARED counter
--    (ticket 04, point 5): Telegram and Max requests by the same user spend the
--    same budget, so the Telegram-only `telegram_daily_usage` table is renamed to
--    `import_bot_daily_usage`. Rows are preserved (data is not lost).
-- 2) Add the Max channel tables: account link (chat_id <-> user_id), one-time
--    link tokens, and pending confirmations awaiting an inline-button tap.

ALTER TABLE telegram_daily_usage RENAME TO import_bot_daily_usage;
ALTER TABLE import_bot_daily_usage RENAME CONSTRAINT uq_telegram_daily_usage TO uq_import_bot_daily_usage;
ALTER INDEX IF EXISTS idx_telegram_daily_usage_user_date RENAME TO idx_import_bot_daily_usage_user_date;

CREATE TABLE max_link (
    id BIGSERIAL PRIMARY KEY,
    chat_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_max_link_user ON max_link(user_id);

CREATE TABLE max_link_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_max_link_token_user ON max_link_token(user_id);

CREATE TABLE max_pending_import (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    message_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_max_pending_chat ON max_pending_import(chat_id);
CREATE INDEX idx_max_pending_user ON max_pending_import(user_id);
