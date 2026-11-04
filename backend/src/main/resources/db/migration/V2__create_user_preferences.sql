-- Phase 0 is single-user: exactly one preferences row (id = 1) is allowed.
CREATE TABLE user_preferences (
    id                      SMALLINT     PRIMARY KEY,
    timezone                VARCHAR(64)  NOT NULL DEFAULT 'Asia/Colombo',
    daily_available_minutes INTEGER      NOT NULL DEFAULT 480,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_preferences_single_row CHECK (id = 1),
    CONSTRAINT chk_user_preferences_minutes CHECK (daily_available_minutes BETWEEN 0 AND 1440)
);

INSERT INTO user_preferences (id, timezone, daily_available_minutes)
VALUES (1, 'Asia/Colombo', 480);
