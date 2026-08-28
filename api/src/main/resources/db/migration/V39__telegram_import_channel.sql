-- Release 0.7, ticket 03: Telegram import channel
-- Link table (chat_id <-> user_id), one-time link tokens, pending confirmations,
-- and a per-user daily request counter for the import bot rate limit.

CREATE TABLE telegram_link (
    id BIGSERIAL PRIMARY KEY,
    chat_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_link_user ON telegram_link(user_id);

CREATE TABLE telegram_link_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_link_token_user ON telegram_link_token(user_id);

CREATE TABLE telegram_pending_import (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_pending_chat ON telegram_pending_import(chat_id);
CREATE INDEX idx_telegram_pending_user ON telegram_pending_import(user_id);

CREATE TABLE telegram_daily_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_telegram_daily_usage UNIQUE (user_id, usage_date)
);
CREATE INDEX idx_telegram_daily_usage_user_date ON telegram_daily_usage(user_id, usage_date);
