-- IA-01: registro de uso de la capa de IA, para aplicar los límites de costo.
-- Nunca se guarda el contenido de los prompts ni de las respuestas. Los costos van como enteros en micro-USD
-- (1 USD = 1.000.000) para no depender de decimales flotantes.

-- Una fila por intento de llamada. No tiene user_id: es contabilidad global (presupuesto diario y mensual).
CREATE TABLE ai_usage (
    id             UUID PRIMARY KEY,
    -- Día en UTC; se guarda aparte para sumar por día y por mes con un índice simple.
    used_on        DATE         NOT NULL,
    purpose        VARCHAR(40)  NOT NULL,
    model          VARCHAR(80)  NOT NULL,
    tokens_in      INTEGER      NOT NULL DEFAULT 0,
    tokens_out     INTEGER      NOT NULL DEFAULT 0,
    cost_micro_usd BIGINT       NOT NULL DEFAULT 0,
    result         VARCHAR(10)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_ai_usage_result CHECK (result IN ('OK', 'ERROR', 'BLOCKED')),
    CONSTRAINT ck_ai_usage_amounts CHECK (tokens_in >= 0 AND tokens_out >= 0 AND cost_micro_usd >= 0)
);

CREATE INDEX ix_ai_usage_used_on ON ai_usage (used_on);

-- Cuota por usuario y día, solo para usos disparados por una persona (por ejemplo el feedback).
-- Solo guarda contadores; se borra junto con el usuario.
CREATE TABLE ai_user_quota (
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    used_on        DATE        NOT NULL,
    calls          INTEGER     NOT NULL DEFAULT 0,
    cost_micro_usd BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, used_on),
    CONSTRAINT ck_ai_user_quota_amounts CHECK (calls >= 0 AND cost_micro_usd >= 0)
);
