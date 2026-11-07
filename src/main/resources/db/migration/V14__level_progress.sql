-- Progreso dentro del nivel (0 = piso, 100 = techo). Cada actividad suma o resta puntos y el nivel cambia al
-- llegar a un extremo (docs/plan-backend.md, sección 7). Los perfiles existentes arrancan en el medio.
ALTER TABLE performance_profiles
    ADD COLUMN level_progress SMALLINT NOT NULL DEFAULT 50,
    ADD CONSTRAINT ck_performance_profiles_level_progress CHECK (level_progress BETWEEN 0 AND 100);
