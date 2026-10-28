-- BACK-04: modelo de ejercicios (categorías, ejercicios y niveles) y seed inicial.
-- Agregar categorías, ejercicios o niveles se hace con una migración nueva, sin cambiar código.

CREATE TABLE categories (
    id          UUID        PRIMARY KEY,
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    description TEXT,
    mvp         BOOLEAN     NOT NULL DEFAULT FALSE,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_categories_code UNIQUE (code)
);

CREATE TABLE exercises (
    id           UUID        PRIMARY KEY,
    code         VARCHAR(40) NOT NULL,
    name         VARCHAR(80) NOT NULL,
    type         VARCHAR(30) NOT NULL,
    category_id  UUID        NOT NULL REFERENCES categories (id),
    modality     VARCHAR(10) NOT NULL,
    instructions TEXT        NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT FALSE,
    post_mvp     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_exercises_code UNIQUE (code),
    CONSTRAINT ck_exercises_modality CHECK (modality IN ('VISUAL', 'AUDITORY'))
);

CREATE INDEX ix_exercises_category ON exercises (category_id);

CREATE TABLE exercise_levels (
    exercise_id        UUID     NOT NULL REFERENCES exercises (id),
    level              SMALLINT NOT NULL,
    params             JSONB    NOT NULL,
    estimated_seconds  INTEGER  NOT NULL,
    target_response_ms INTEGER,
    PRIMARY KEY (exercise_id, level),
    CONSTRAINT ck_exercise_levels_level CHECK (level >= 1),
    CONSTRAINT ck_exercise_levels_estimated_seconds CHECK (estimated_seconds > 0),
    CONSTRAINT ck_exercise_levels_target_response_ms CHECK (target_response_ms IS NULL OR target_response_ms > 0)
);

-- Categorías. Memoria asociativa, secuencial y episódica no se siembran: se agregan cuando se definan.
-- MEMORIA_AUDITIVA es SHOULD: queda inactiva hasta que tenga ejercicios activos.
INSERT INTO categories (id, code, name, description, mvp, active) VALUES
    (gen_random_uuid(), 'ATENCION', 'Atención',
     'Actividades para practicar el foco y la respuesta selectiva ante estímulos.', TRUE, TRUE),
    (gen_random_uuid(), 'MEMORIA_VISUAL', 'Memoria visual',
     'Actividades para recordar imágenes, posiciones y patrones.', TRUE, TRUE),
    (gen_random_uuid(), 'MEMORIA_TRABAJO', 'Memoria de trabajo',
     'Actividades para retener y reorganizar información por unos segundos.', TRUE, TRUE),
    (gen_random_uuid(), 'MEMORIA_AUDITIVA', 'Memoria auditiva',
     'Actividades para recordar secuencias de sonidos.', FALSE, FALSE);

-- Un ejercicio por mecánica del front.
INSERT INTO exercises (id, code, name, type, category_id, modality, instructions, active, post_mvp)
SELECT gen_random_uuid(), v.code, v.name, v.type, c.id, v.modality, v.instructions, v.active, FALSE
FROM (VALUES
    ('VISUAL_RECOGNITION', 'Reconocimiento visual', 'VISUAL_RECOGNITION', 'MEMORIA_VISUAL', 'VISUAL',
     'Mirá las imágenes que aparecen. Después vas a ver más imágenes: marcá solo las que ya viste.', TRUE),
    ('SPATIAL_GRID', 'Cuadrícula', 'SPATIAL_GRID', 'MEMORIA_VISUAL', 'VISUAL',
     'Algunas casillas se van a iluminar. Cuando se apaguen, tocá las mismas casillas.', TRUE),
    ('ODD_ONE_OUT', 'Elemento diferente', 'ODD_ONE_OUT', 'ATENCION', 'VISUAL',
     'Encontrá el elemento que es distinto a los demás lo más rápido que puedas.', TRUE),
    ('STROOP', 'Color y palabra', 'STROOP', 'ATENCION', 'VISUAL',
     'Elegí el color de la tinta con la que está escrita la palabra, no lo que dice la palabra.', TRUE),
    ('REVERSE_SEQUENCE', 'Secuencia inversa', 'REVERSE_SEQUENCE', 'MEMORIA_TRABAJO', 'VISUAL',
     'Vas a ver una secuencia de elementos. Repetila en el orden inverso.', TRUE),
    ('N_BACK', 'N-back', 'N_BACK', 'MEMORIA_TRABAJO', 'VISUAL',
     'Tocá el botón cuando el elemento actual sea igual al que apareció N posiciones antes.', TRUE),
    ('AUDITORY_SEQUENCE', 'Secuencia de sonidos', 'AUDITORY_SEQUENCE', 'MEMORIA_AUDITIVA', 'AUDITORY',
     'Escuchá la secuencia de sonidos y repetila en el mismo orden.', FALSE)
) AS v(code, name, type, category_code, modality, instructions, active)
JOIN categories c ON c.code = v.category_code;

-- Niveles 1 a 10 con una progresión lineal simple (propuesta inicial, ajustar con el equipo).
-- params por tipo:
--   VISUAL_RECOGNITION: itemsToMemorize, distractors, exposureMsPerItem
--   SPATIAL_GRID:       gridSize, cellsToRemember, exposureMs
--   ODD_ONE_OUT:        items, similarity (0 a 1, más alto = más parecidos), timeLimitMs
--   STROOP:             trials, incongruentRatio, responseLimitMs
--   REVERSE_SEQUENCE:   sequenceLength, itemDisplayMs
--   N_BACK:             n, trials, matchRatio, stimulusMs
--   AUDITORY_SEQUENCE:  sequenceLength, itemIntervalMs
-- target_response_ms solo se define en las mecánicas donde la velocidad es parte del desempeño.
INSERT INTO exercise_levels (exercise_id, level, params, estimated_seconds, target_response_ms)
SELECT e.id,
       g.l,
       CASE e.type
           WHEN 'VISUAL_RECOGNITION' THEN jsonb_build_object(
                   'itemsToMemorize', 2 + g.l,
                   'distractors', 2 + g.l,
                   'exposureMsPerItem', 1600 - 100 * g.l)
           WHEN 'SPATIAL_GRID' THEN jsonb_build_object(
                   'gridSize', 3 + (g.l - 1) / 3,
                   'cellsToRemember', 2 + g.l,
                   'exposureMs', 2600 - 100 * g.l)
           WHEN 'ODD_ONE_OUT' THEN jsonb_build_object(
                   'items', 6 + 2 * g.l,
                   'similarity', round(0.1 * g.l, 2),
                   'timeLimitMs', 8000 - 400 * g.l)
           WHEN 'STROOP' THEN jsonb_build_object(
                   'trials', 10 + g.l,
                   'incongruentRatio', round(LEAST(0.25 + 0.05 * g.l, 0.75), 2),
                   'responseLimitMs', 3000 - 150 * g.l)
           WHEN 'REVERSE_SEQUENCE' THEN jsonb_build_object(
                   'sequenceLength', 2 + g.l,
                   'itemDisplayMs', 1000 - 40 * g.l)
           WHEN 'N_BACK' THEN jsonb_build_object(
                   'n', 1 + (g.l - 1) / 3,
                   'trials', 20,
                   'matchRatio', 0.3,
                   'stimulusMs', 2500 - 100 * g.l)
           WHEN 'AUDITORY_SEQUENCE' THEN jsonb_build_object(
                   'sequenceLength', 2 + g.l,
                   'itemIntervalMs', 1000 - 40 * g.l)
       END,
       CASE e.type
           WHEN 'VISUAL_RECOGNITION' THEN 45 + 5 * g.l
           WHEN 'SPATIAL_GRID' THEN 40 + 5 * g.l
           WHEN 'ODD_ONE_OUT' THEN 45 + 3 * g.l
           WHEN 'STROOP' THEN 50 + 4 * g.l
           WHEN 'REVERSE_SEQUENCE' THEN 40 + 6 * g.l
           WHEN 'N_BACK' THEN 60 + 5 * g.l
           WHEN 'AUDITORY_SEQUENCE' THEN 40 + 6 * g.l
       END,
       CASE e.type
           WHEN 'ODD_ONE_OUT' THEN 4000 - 200 * g.l
           WHEN 'STROOP' THEN 1500 - 60 * g.l
           WHEN 'N_BACK' THEN 1500 - 50 * g.l
       END
FROM exercises e
CROSS JOIN generate_series(1, 10) AS g(l);
