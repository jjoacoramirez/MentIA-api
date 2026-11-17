-- BACK-18: gamificación básica. XP y nivel general por usuario, y lo ganado en cada sesión
-- (para mostrarlo en el resultado y para no otorgar dos veces lo mismo).

CREATE TABLE user_progress (
    user_id            UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    xp                 INTEGER     NOT NULL DEFAULT 0,
    level              INTEGER     NOT NULL DEFAULT 1,
    sessions_completed INTEGER     NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_progress_xp CHECK (xp >= 0),
    CONSTRAINT ck_user_progress_level CHECK (level >= 1)
);

CREATE TABLE session_rewards (
    session_id UUID        PRIMARY KEY REFERENCES training_sessions (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    xp_earned  INTEGER     NOT NULL,
    xp_total   INTEGER     NOT NULL,
    level      INTEGER     NOT NULL,
    leveled_up BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
