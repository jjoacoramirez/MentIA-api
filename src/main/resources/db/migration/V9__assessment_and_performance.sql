-- BACK-07: evaluación inicial.
--  * performance_profiles: score y nivel por categoría. Nace acá con el nivel inicial; BACK-08 lo actualiza por sesión.
--  * session_items guarda también la duración estimada y el tiempo objetivo del nivel (snapshot, como params).
--  * users.role: ADMIN habilita herramientas de debug, como saltear la evaluación.

ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));

-- Todavía no hay sesiones reales (la generación llega en esta misma ola), así que el default es solo para el ALTER.
ALTER TABLE session_items
    ADD COLUMN estimated_seconds  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN target_response_ms INTEGER;
ALTER TABLE session_items ALTER COLUMN estimated_seconds DROP DEFAULT;

CREATE INDEX ix_training_sessions_user_type_status ON training_sessions (user_id, type, status);

CREATE TABLE performance_profiles (
    user_id     UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id UUID             NOT NULL REFERENCES categories (id),
    score       DOUBLE PRECISION NOT NULL,
    level       SMALLINT         NOT NULL,
    samples     INTEGER          NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (user_id, category_id),
    CONSTRAINT ck_performance_profiles_score CHECK (score >= 0 AND score <= 100),
    CONSTRAINT ck_performance_profiles_level CHECK (level >= 1),
    CONSTRAINT ck_performance_profiles_samples CHECK (samples >= 0)
);
