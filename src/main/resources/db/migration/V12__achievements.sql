-- BACK-19: logros de uso y constancia. El catálogo es dato: sumar un logro es una fila con una condición
-- de un tipo conocido (sessions_completed, level_reached, categories_practiced).

CREATE TABLE achievements (
    code        VARCHAR(40) PRIMARY KEY,
    name        VARCHAR(80) NOT NULL,
    description TEXT        NOT NULL,
    condition   JSONB       NOT NULL,
    position    SMALLINT    NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE user_achievements (
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    achievement_code VARCHAR(40) NOT NULL REFERENCES achievements (code),
    unlocked_at      TIMESTAMPTZ NOT NULL,
    -- Sesión que lo desbloqueó, para mostrarlo en su resultado.
    session_id       UUID        REFERENCES training_sessions (id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, achievement_code)
);

CREATE INDEX ix_user_achievements_session ON user_achievements (session_id);

-- Categorías en las que el usuario completó al menos un ejercicio (para "practicar todas las categorías").
CREATE TABLE user_categories_practiced (
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, category_code)
);

INSERT INTO achievements (code, name, description, condition, position) VALUES
    ('FIRST_SESSION', 'Primer paso', 'Completaste tu primera sesión en MentIA.',
     '{"type": "sessions_completed", "min": 1}', 1),
    ('FIVE_SESSIONS', 'Constancia', 'Completaste 5 sesiones.',
     '{"type": "sessions_completed", "min": 5}', 2),
    ('TEN_SESSIONS', 'Ritmo propio', 'Completaste 10 sesiones.',
     '{"type": "sessions_completed", "min": 10}', 3),
    ('LEVEL_3', 'Nivel 3', 'Llegaste al nivel 3 de MentIA.',
     '{"type": "level_reached", "min": 3}', 4),
    ('ALL_CATEGORIES', 'De todo un poco', 'Hiciste actividades de las 3 categorías.',
     '{"type": "categories_practiced", "min": 3}', 5);
