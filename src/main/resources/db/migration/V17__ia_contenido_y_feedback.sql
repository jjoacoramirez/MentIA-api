-- IA: contenido generado por lotes y comentario de cierre de sesión.
--
--  * exercise_variants.source: de dónde viene cada variante (SEED = migración, AI = generada y validada). Sirve para
--    revisar o desactivar lo generado sin tocar lo curado a mano.
--  * session_feedback: el comentario de una sesión, uno por sesión. Se guarda para no volver a llamar a la IA (ni
--    gastar la cuota de la persona) cada vez que se abre el resultado. Solo texto: sin prompts ni datos del proveedor.

ALTER TABLE exercise_variants ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'SEED';
ALTER TABLE exercise_variants ADD CONSTRAINT ck_exercise_variants_source CHECK (source IN ('SEED', 'AI'));

CREATE TABLE session_feedback (
    session_id UUID         PRIMARY KEY REFERENCES training_sessions (id) ON DELETE CASCADE,
    text       VARCHAR(600) NOT NULL,
    source     VARCHAR(10)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_session_feedback_source CHECK (source IN ('AI', 'BASIC'))
);
