-- BACK-01: usuarios seudonimizados y perfil de entrenamiento inicial.
-- Solo datos permitidos: username, hash de contraseña, UUID aleatorio, rango etario y configuración del entrenamiento.

CREATE TABLE users (
    id                 UUID         PRIMARY KEY,
    username           VARCHAR(30)  NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    recovery_code_hash VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[a-z0-9_.]{3,30}$')
);

CREATE TABLE training_profiles (
    user_id              UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    age_range            VARCHAR(10) NOT NULL,
    education_level      VARCHAR(30),
    session_minutes      SMALLINT    NOT NULL DEFAULT 10,
    goals                JSONB       NOT NULL DEFAULT '[]'::jsonb,
    onboarding_completed BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_training_profiles_age_range CHECK (age_range IN ('18-24', '25-30', '31-40', '41+')),
    CONSTRAINT ck_training_profiles_session_minutes CHECK (session_minutes BETWEEN 5 AND 30)
);
