-- Ajustes de contenido pedidos en la revisión de la UI:
--  * ODD_ONE_OUT 'figuras' pasa de emoji a símbolos geométricos (de más fácil a más difícil).
--  * Consignas más cortas y directas. La de N_BACK ya no habla de "N posiciones": la UI muestra
--    cuántos lugares atrás mirar según el nivel.

UPDATE exercise_variants v
SET content = '{"kind": "symbols", "pairs": [["■","●"],["▲","▼"],["★","☆"],["◆","◇"],["◐","◑"],["○","◎"]]}'::jsonb
FROM exercises e
WHERE v.exercise_id = e.id AND e.code = 'ODD_ONE_OUT' AND v.variant_key = 'figuras';

UPDATE exercises e
SET instructions = v.instructions
FROM (VALUES
    ('VISUAL_RECOGNITION',
     'Primero vas a ver algunas imágenes: tratá de recordarlas. Después aparecen mezcladas con otras nuevas. Marcá las que ya habías visto y confirmá.'),
    ('SPATIAL_GRID',
     'Mirá qué casillas se encienden. Cuando se apaguen, tocá esas mismas casillas.'),
    ('ODD_ONE_OUT',
     'En cada ronda hay un elemento que no es igual al resto. Tocalo apenas lo encuentres.'),
    ('STROOP',
     'Fijate de qué color está pintada la palabra y elegí ese color. No te guíes por lo que dice.'),
    ('REVERSE_SEQUENCE',
     'Vas a ver una secuencia corta. Cuando termine, armala al revés: del último al primero.'),
    ('N_BACK',
     'Los símbolos aparecen de a uno. Tocá «Coincide» cuando el que ves sea igual al que apareció unos lugares antes. Antes de empezar te decimos cuántos lugares mirar.'),
    ('AUDITORY_SEQUENCE',
     'Escuchá los sonidos y repetilos en el mismo orden.')
) AS v(code, instructions)
WHERE e.code = v.code;
