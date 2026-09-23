CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(320) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE user_profiles (
    user_id      UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE email_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL CHECK (type IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_tokens_user_type ON email_tokens (user_id, type);
