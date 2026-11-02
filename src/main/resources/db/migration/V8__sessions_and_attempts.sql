-- BACK-12: sesiones, ítems de sesión e intentos. La lógica de generación de sesiones llega con BACK-07 y BACK-09;
-- acá se crean las tablas que ambos necesitan y el registro de intentos.

CREATE TABLE training_sessions (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id),
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    planned_minutes SMALLINT    NOT NULL,
    rationale       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    result          JSONB,
    CONSTRAINT ck_training_sessions_type CHECK (type IN ('INITIAL_ASSESSMENT', 'TRAINING')),
    CONSTRAINT ck_training_sessions_status CHECK (status IN ('GENERATED', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_training_sessions_planned_minutes CHECK (planned_minutes > 0)
);

CREATE INDEX ix_training_sessions_user_created ON training_sessions (user_id, created_at DESC);

CREATE TABLE session_items (
    id               UUID     PRIMARY KEY,
    session_id       UUID     NOT NULL REFERENCES training_sessions (id) ON DELETE CASCADE,
    position         SMALLINT NOT NULL,
    exercise_id      UUID     NOT NULL REFERENCES exercises (id),
    variant_id       UUID     NOT NULL REFERENCES exercise_variants (id),
    category_id      UUID     NOT NULL REFERENCES categories (id),
    difficulty_level SMALLINT NOT NULL,
    seed             BIGINT   NOT NULL,
    params           JSONB    NOT NULL,
    CONSTRAINT uk_session_items_position UNIQUE (session_id, position),
    CONSTRAINT ck_session_items_position CHECK (position >= 1),
    CONSTRAINT ck_session_items_difficulty_level CHECK (difficulty_level >= 1)
);

-- Exercise, variante, categoría y nivel se copian del ítem: el intento queda completo aunque el catálogo cambie.
CREATE TABLE attempts (
    id                UUID             PRIMARY KEY,
    client_attempt_id UUID             NOT NULL,
    session_item_id   UUID             NOT NULL REFERENCES session_items (id),
    session_id        UUID             NOT NULL REFERENCES training_sessions (id),
    user_id           UUID             NOT NULL REFERENCES users (id),
    exercise_id       UUID             NOT NULL REFERENCES exercises (id),
    variant_id        UUID             NOT NULL REFERENCES exercise_variants (id),
    category_id       UUID             NOT NULL REFERENCES categories (id),
    difficulty_level  SMALLINT         NOT NULL,
    response          JSONB,
    is_correct        BOOLEAN          NOT NULL,
    accuracy          DOUBLE PRECISION NOT NULL,
    response_time_ms  INTEGER          NOT NULL,
    metrics           JSONB,
    created_at        TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uk_attempts_session_item UNIQUE (session_item_id),
    CONSTRAINT uk_attempts_client_attempt UNIQUE (user_id, client_attempt_id),
    CONSTRAINT ck_attempts_accuracy CHECK (accuracy >= 0 AND accuracy <= 1),
    CONSTRAINT ck_attempts_response_time CHECK (response_time_ms >= 0)
);

CREATE INDEX ix_attempts_user_created ON attempts (user_id, created_at);
CREATE INDEX ix_attempts_user_variant_created ON attempts (user_id, variant_id, created_at);
CREATE INDEX ix_attempts_session ON attempts (session_id);
