# Gestión de contenido: categorías, ejercicios, niveles y variantes

En el MVP no hay panel de administración. El contenido se gestiona con **migraciones Flyway** en `src/main/resources/db/migration`. Sumar contenido es agregar filas, sin tocar código Java, salvo que se trate de una **mecánica nueva** (ver al final).

Reglas que valen para todo:

- **Una migración aplicada no se edita.** Cada cambio (alta, corrección, activación) es una migración nueva `V{n}__descripcion.sql`, con `n` mayor que la última.
- **Todo entra inactivo** (`active = false`) y se activa en una migración aparte, después de revisarlo (ver "Revisión antes de activar").
- **Nada de lenguaje clínico** en nombres, descripciones, instrucciones ni contenido: nada de "diagnóstico", "capacidad de memoria", "mejora cognitiva" ni "déficit". `ForbiddenTermsTest` lo controla en cada build.
- Los textos para el usuario van en español rioplatense: "Mirá", "tocá", "elegí".

## Modelo

| Tabla | Qué es | Clave para referenciarla en SQL |
|---|---|---|
| `categories` | Lo que se entrena (atención, memoria visual…). `mvp` marca las del MVP. | `code` (ej. `MEMORIA_TRABAJO`) |
| `exercises` | Una mecánica concreta dentro de una categoría. | `code` (ej. `N_BACK`) |
| `exercise_levels` | Parámetros de dificultad por nivel, del 1 al 10. | (`exercise_id`, `level`) |
| `exercise_variants` | Conjunto de estímulos: tema de imágenes, alfabeto, paleta. | (`exercise_id`, `variant_key`) |

Los ids son UUID generados con `gen_random_uuid()`. Entre tablas, en las migraciones, siempre se referencia por `code` o `variant_key`, nunca por un UUID copiado de otra base.

## Agregar una categoría

```sql
-- V12__categoria_memoria_secuencial.sql
INSERT INTO categories (id, code, name, description, mvp, active)
VALUES (gen_random_uuid(), 'MEMORIA_SECUENCIAL', 'Memoria secuencial',
        'Actividades para recordar el orden en que aparecen las cosas.', FALSE, FALSE);
```

- `code` en mayúsculas con guiones bajos. No se cambia nunca: lo usan los objetivos del perfil y el historial.
- Una categoría activa sin ejercicios activos aparece en `GET /categories`, pero no se puede entrenar. Activala recién cuando tenga ejercicios.

## Agregar un ejercicio de una mecánica existente

```sql
-- V13__ejercicio_secuencia_inversa_colores.sql
INSERT INTO exercises (id, code, name, type, category_id, modality, instructions, active, post_mvp)
SELECT gen_random_uuid(), 'REVERSE_COLORS', 'Colores al revés', 'REVERSE_SEQUENCE', c.id, 'VISUAL',
       'Vas a ver una secuencia de colores. Repetila en el orden inverso.', FALSE, FALSE
FROM categories c WHERE c.code = 'MEMORIA_TRABAJO';
```

- `type` es una de las mecánicas que el front sabe ejecutar: `VISUAL_RECOGNITION`, `SPATIAL_GRID`, `ODD_ONE_OUT`, `STROOP`, `REVERSE_SEQUENCE`, `N_BACK`, `AUDITORY_SEQUENCE`.
- `modality`: `VISUAL` o `AUDITORY`.
- Un ejercicio activo dentro de una categoría inactiva no se usa. Al arrancar, la API lo avisa con un WARN.

## Agregar los niveles

Cada ejercicio necesita los niveles **1 a 10**. La forma de `params` depende del `type`:

| `type` | `params` |
|---|---|
| `VISUAL_RECOGNITION` | `itemsToMemorize`, `distractors`, `exposureMsPerItem` |
| `SPATIAL_GRID` | `gridSize`, `cellsToRemember`, `exposureMs` |
| `ODD_ONE_OUT` | `items`, `similarity` (0 a 1), `timeLimitMs` |
| `STROOP` | `trials`, `incongruentRatio`, `responseLimitMs` |
| `REVERSE_SEQUENCE` | `sequenceLength`, `itemDisplayMs` |
| `N_BACK` | `n`, `trials`, `matchRatio`, `stimulusMs` |
| `AUDITORY_SEQUENCE` | `sequenceLength`, `itemIntervalMs` |

Además de `params`, cada nivel lleva:

- `estimated_seconds`: duración aproximada del ítem. Se usa para llenar el tiempo de una sesión.
- `target_response_ms`: solo en las mecánicas donde la velocidad cuenta (hoy `ODD_ONE_OUT`, `STROOP` y `N_BACK`). Si es `NULL`, el motor no mira el tiempo para subir de nivel.

Lo más simple es generar los 10 niveles con `generate_series`, como en `V3__catalog_exercises.sql`:

```sql
INSERT INTO exercise_levels (exercise_id, level, params, estimated_seconds, target_response_ms)
SELECT e.id, g.l,
       jsonb_build_object('sequenceLength', 2 + g.l, 'itemDisplayMs', 1000 - 40 * g.l),
       40 + 6 * g.l,
       NULL
FROM exercises e CROSS JOIN generate_series(1, 10) AS g(l)
WHERE e.code = 'REVERSE_COLORS';
```

Para corregir los parámetros de un nivel, la migración nueva usa `UPDATE exercise_levels SET params = ... WHERE ...`. Las sesiones ya generadas no cambian, porque cada ítem guarda una copia de sus `params`.

## Agregar variantes

Cada ejercicio activo necesita **al menos 3 variantes activas**, para que la rotación tenga de dónde elegir. La forma de `content` depende del `type`:

| `type` | `content` |
|---|---|
| `VISUAL_RECOGNITION` | `theme`, `items`: al menos `itemsToMemorize + distractors` del nivel 10, sin repetidos (hoy, 24) |
| `SPATIAL_GRID` | `highlightColor` (hex), `cellShape` (`square`, `rounded`, `circle`) |
| `ODD_ONE_OUT` | `kind`, `pairs`: lista de `[base, distinto]`, de más fácil a más difícil |
| `STROOP` | `colors`: lista de `{name, hex}`. El `name` es la palabra que se muestra. |
| `REVERSE_SEQUENCE` | `symbols` |
| `N_BACK` | `symbols` |

```sql
-- V14__variantes_reverse_colors.sql
INSERT INTO exercise_variants (id, exercise_id, variant_key, content, active)
SELECT gen_random_uuid(), e.id, v.variant_key, v.content::jsonb, FALSE
FROM (VALUES
    ('calidos', '{"symbols": ["🟥","🟧","🟨","🟫"]}'),
    ('frios',   '{"symbols": ["🟦","🟩","🟪","⬛"]}'),
    ('mixtos',  '{"symbols": ["🟥","🟦","🟨","🟩","🟪"]}')
) AS v(variant_key, content)
JOIN exercises e ON e.code = 'REVERSE_COLORS';
```

- `variant_key` en minúsculas con guiones, única dentro del ejercicio. No se cambia: el historial de rotación se apoya en ella.
- Para retirar una variante se la desactiva (`active = false`). No se borra, porque hay sesiones e intentos que la referencian.

## Revisión antes de activar

1. **Correr la migración localmente** (`docker compose up -d db` y `./mvnw spring-boot:run`) y revisar lo cargado:

   ```sql
   SELECT e.code, e.type, c.code AS categoria, e.active,
          (SELECT count(*) FROM exercise_levels l WHERE l.exercise_id = e.id) AS niveles,
          (SELECT count(*) FROM exercise_variants v WHERE v.exercise_id = e.id) AS variantes
   FROM exercises e JOIN categories c ON c.id = e.category_id
   ORDER BY c.code, e.code;
   ```

2. **Checklist de contenido**:
   - [ ] Instrucciones claras, en segunda persona y sin lenguaje clínico.
   - [ ] Niveles 1 a 10, con la dificultad creciendo de forma pareja.
   - [ ] `estimated_seconds` razonable (probalo en el front).
   - [ ] `target_response_ms` solo si la velocidad es parte de la mecánica.
   - [ ] Al menos 3 variantes, con estímulos suficientes para el nivel 10 y fáciles de distinguir entre sí.
   - [ ] Probado en el front al menos en los niveles 1, 5 y 10.

3. **Activar** en una migración aparte:

   ```sql
   -- V15__activar_reverse_colors.sql
   UPDATE exercise_variants SET active = TRUE
   WHERE exercise_id = (SELECT id FROM exercises WHERE code = 'REVERSE_COLORS');
   UPDATE exercises SET active = TRUE WHERE code = 'REVERSE_COLORS';
   ```

4. `./mvnw verify` tiene que pasar. Estos tests controlan el contenido:
   - `ForbiddenTermsTest`: no hay términos clínicos en código, migraciones ni contrato.
   - `CatalogContractTest`: los tipos coinciden con `openapi.yaml` y cada mecánica activa tiene al menos 3 variantes en el seed.
   - `CatalogSeedIntegrationTest` y `VariantSeedIntegrationTest`, contra PostgreSQL:
     - cada ejercicio tiene niveles 1 a 10 con parámetros;
     - cada ejercicio activo tiene al menos 3 variantes con contenido;
     - ningún ejercicio activo queda en una categoría inactiva;
     - reconocimiento visual tiene estímulos suficientes para el nivel 10.

## Una mecánica nueva sí requiere código

Agregar un `type` nuevo (por ejemplo, un juego de memoria asociativa) no es solo contenido: el front tiene que saber ejecutarlo. Hace falta:

1. Implementar la mecánica en el front.
2. Agregar el valor al enum `ExerciseType` en `openapi.yaml` y en `com.mentia.catalog.domain.ExerciseType`. `CatalogContractTest` falla si no coinciden.
3. Documentar sus `params` y su `content` en este archivo.
4. Recién ahí, cargar el ejercicio, los niveles y las variantes como se explica arriba.
