-- Schema for the Veolia Jur outage notifier bot.
-- Run automatically on every startup (spring.sql.init.mode=always); every
-- statement is idempotent so it never touches existing data.

CREATE TABLE IF NOT EXISTS users (
    chat_id     INTEGER PRIMARY KEY,
    language    TEXT NOT NULL DEFAULT 'HY',
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users (chat_id),
    keyword     TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (user_id, keyword)
);

CREATE TABLE IF NOT EXISTS processed_posts (
    post_id       TEXT PRIMARY KEY,
    processed_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions (user_id);
