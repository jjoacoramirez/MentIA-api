-- Recordatorios por notificación push (web push). Solo se guarda la URL de suscripción del navegador:
-- no se guardan claves de cifrado porque el aviso se manda sin contenido. No es un dato personal, pero identifica
-- un dispositivo: no se loguea ni se devuelve en ninguna respuesta.

CREATE TABLE push_subscriptions (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint         VARCHAR(2048) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_reminded_on DATE,
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);
