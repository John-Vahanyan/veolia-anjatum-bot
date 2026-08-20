-- Schema for the Veolia Jur outage notifier bot.
-- Run automatically on every startup (spring.sql.init.mode=always); every
-- statement is idempotent so it never touches existing data.

CREATE TABLE IF NOT EXISTS users (
    chat_id     INTEGER PRIMARY KEY,
    language    TEXT NOT NULL DEFAULT 'HY',
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

-- region_code/district_code are non-null (default '') rather than nullable so
-- the UNIQUE constraint below treats "no scope" consistently across rows —
-- SQLite (like standard SQL) treats every NULL as distinct from every other
-- NULL, which would silently defeat de-duplication for unscoped keywords.
CREATE TABLE IF NOT EXISTS subscriptions (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id            INTEGER NOT NULL REFERENCES users (chat_id),
    keyword            TEXT NOT NULL,
    region_code        TEXT NOT NULL DEFAULT '',
    district_code      TEXT NOT NULL DEFAULT '',
    subscription_type  TEXT NOT NULL DEFAULT 'KEYWORD' CHECK (subscription_type IN ('KEYWORD', 'SCOPED_STREET')),
    -- 1 iff the user typed this keyword in Latin/Cyrillic and it was auto-transliterated
    -- to Armenian (so it's an approximation, worth 2 letters of matching tolerance); 0 for
    -- a keyword typed directly in Armenian, or a canonical region/district name from a button.
    fuzzy_match        INTEGER NOT NULL DEFAULT 1,
    created_at         TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (user_id, keyword, region_code, district_code)
);

CREATE TABLE IF NOT EXISTS processed_posts (
    post_id       TEXT PRIMARY KEY,
    processed_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Permanent audit trail of subscribe/unsubscribe actions. Unlike the
-- `subscriptions` table itself (which only reflects *current* state and
-- loses all trace of a keyword once it's removed), rows here are never
-- deleted, so "who subscribed/unsubscribed to what, and when" stays
-- queryable indefinitely.
CREATE TABLE IF NOT EXISTS subscription_events (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL,
    keyword       TEXT NOT NULL,
    action        TEXT NOT NULL CHECK (action IN ('SUBSCRIBE', 'UNSUBSCRIBE')),
    occurred_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions (user_id);
CREATE INDEX IF NOT EXISTS idx_subscription_events_user_id ON subscription_events (user_id);
