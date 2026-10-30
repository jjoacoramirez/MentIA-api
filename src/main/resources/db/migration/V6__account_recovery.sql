-- BACK-03B: recuperación de cuenta con código.
-- Límite de intentos por username y versión de tokens por usuario (el reset invalida todos los tokens emitidos).

ALTER TABLE users
    ADD COLUMN recovery_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN recovery_locked_until    TIMESTAMPTZ;

CREATE TABLE user_token_versions (
    user_id UUID    PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    version INTEGER NOT NULL
);
