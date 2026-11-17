-- BACK-03: denylist de tokens revocados por logout. Se guardan hasta su vencimiento y un job los limpia.

CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_revoked_tokens_expires_at ON revoked_tokens (expires_at);
