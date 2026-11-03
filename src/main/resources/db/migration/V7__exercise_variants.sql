-- BACK-05: banco de variantes. Cada variante es un conjunto de estímulos para una mecánica;
-- el cliente genera los estímulos concretos con params (del nivel), content (de la variante) y un seed.
-- El uso reciente por usuario se deriva de session_items y attempts (BACK-11), sin tabla extra.

CREATE TABLE exercise_variants (
    id          UUID        PRIMARY KEY,
    exercise_id UUID        NOT NULL REFERENCES exercises (id),
    variant_key VARCHAR(40) NOT NULL,
    content     JSONB       NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_exercise_variants_key UNIQUE (exercise_id, variant_key)
);

-- content por tipo:
--   VISUAL_RECOGNITION: items (al menos itemsToMemorize + distractors del nivel 10 = 24)
--   SPATIAL_GRID:       highlightColor, cellShape
--   ODD_ONE_OUT:        kind, pairs ([base, distinto], de más fácil a más difícil)
--   STROOP:             colors ([{name, hex}])
--   REVERSE_SEQUENCE:   symbols
--   N_BACK:             symbols
INSERT INTO exercise_variants (id, exercise_id, variant_key, content, active)
SELECT gen_random_uuid(), e.id, v.variant_key, v.content::jsonb, TRUE
FROM (VALUES
    ('VISUAL_RECOGNITION', 'frutas-y-verduras', '{"theme": "frutas y verduras", "items": ["🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌽","🥕"]}'),
    ('VISUAL_RECOGNITION', 'animales', '{"theme": "animales", "items": ["🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🐺","🐗"]}'),
    ('VISUAL_RECOGNITION', 'objetos', '{"theme": "objetos", "items": ["⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏓","🎸","🎹","🎺","🎻","🥁","🎲","🧩","🎯","🔑","🔒","💡","📷","📚","🧲","🪁","🧸"]}'),

    ('SPATIAL_GRID', 'azul-cuadrado', '{"highlightColor": "#1E88E5", "cellShape": "square"}'),
    ('SPATIAL_GRID', 'verde-redondeado', '{"highlightColor": "#43A047", "cellShape": "rounded"}'),
    ('SPATIAL_GRID', 'violeta-circulo', '{"highlightColor": "#8E24AA", "cellShape": "circle"}'),

    ('ODD_ONE_OUT', 'letras', '{"kind": "letters", "pairs": [["A","V"],["M","W"],["E","F"],["C","G"],["P","R"],["O","Q"]]}'),
    ('ODD_ONE_OUT', 'numeros', '{"kind": "numbers", "pairs": [["2","7"],["3","5"],["1","7"],["6","9"],["3","8"],["5","6"]]}'),
    ('ODD_ONE_OUT', 'figuras', '{"kind": "emoji", "pairs": [["🍎","🍌"],["🐶","🐱"],["⭐","🌙"],["🍎","🍅"],["😀","😃"],["🌕","🌝"]]}'),

    ('STROOP', 'colores-basicos', '{"colors": [{"name": "rojo", "hex": "#E53935"}, {"name": "azul", "hex": "#1E88E5"}, {"name": "verde", "hex": "#43A047"}, {"name": "amarillo", "hex": "#FDD835"}]}'),
    ('STROOP', 'colores-extendidos', '{"colors": [{"name": "rojo", "hex": "#E53935"}, {"name": "azul", "hex": "#1E88E5"}, {"name": "verde", "hex": "#43A047"}, {"name": "amarillo", "hex": "#FDD835"}, {"name": "violeta", "hex": "#8E24AA"}]}'),
    ('STROOP', 'colores-calidos-y-frios', '{"colors": [{"name": "naranja", "hex": "#FB8C00"}, {"name": "celeste", "hex": "#29B6F6"}, {"name": "rosa", "hex": "#EC407A"}, {"name": "gris", "hex": "#757575"}]}'),

    ('REVERSE_SEQUENCE', 'digitos', '{"symbols": ["2","3","4","5","6","7","8","9"]}'),
    ('REVERSE_SEQUENCE', 'letras', '{"symbols": ["B","C","D","F","G","H","J","K","L","M","N","P","R","S","T"]}'),
    ('REVERSE_SEQUENCE', 'animales', '{"symbols": ["🐶","🐱","🐰","🦊","🐻","🐼","🐸","🐧","🦉"]}'),

    ('N_BACK', 'letras', '{"symbols": ["B","C","D","F","G","H","K","L","M","P","R","S"]}'),
    ('N_BACK', 'figuras', '{"symbols": ["●","■","▲","◆","★","♥"]}'),
    ('N_BACK', 'frutas', '{"symbols": ["🍎","🍌","🍇","🍓","🍉","🍍","🥝","🍒"]}')
) AS v(exercise_code, variant_key, content)
JOIN exercises e ON e.code = v.exercise_code;
