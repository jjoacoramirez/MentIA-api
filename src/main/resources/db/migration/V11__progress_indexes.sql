-- BACK-14: índices para las agregaciones de historial y progreso (resumen por estado y fecha de cierre,
-- evolución por categoría a partir de los intentos de cada usuario).

CREATE INDEX ix_training_sessions_user_status_finished ON training_sessions (user_id, status, finished_at);
CREATE INDEX ix_attempts_user_category_created ON attempts (user_id, category_id, created_at);
