# MentIA — Plan de implementación del backend (MVP)

Este plan traduce el backlog BACK-01 a BACK-21 en un orden de trabajo, un modelo de datos y criterios técnicos por ticket. Las dependencias son las cargadas en Jira (proyecto SCRUM). Si el Jira se migra al del grupo (MSDIP), actualizar la tabla de claves de la sección 4.

## 1. Decisiones técnicas

| Tema | Decisión | Estado |
|---|---|---|
| Lenguaje y framework | Java 21 + Spring Boot 3 | Fijada |
| Build | Maven | Propuesta (igual que el TPO de Coyote) |
| Base de datos | PostgreSQL 16 + Flyway. `jsonb` para parámetros y contenido de ejercicios | Propuesta |
| Autenticación | JWT HS256, vigencia 24 h configurable. Logout con denylist por `jti` | Propuesta |
| Hash | BCrypt para contraseñas y códigos de recuperación | Propuesta |
| Errores | `ProblemDetail` con `code` estable | Propuesta |
| Resultado de los ejercicios | El cliente reporta el resultado de cada ejercicio (`isCorrect`, `accuracy`, `metrics`). El servidor no lo recalcula. No hay competencia entre usuarios, así que no se justifica validarlo | Propuesta |
| Generación de estímulos | El servidor envía `params` (según nivel), `content` (conjunto de estímulos de la variante) y un `seed`. El cliente genera los estímulos concretos a partir de eso | Propuesta, **acordar con el front** |
| Día de la racha | Zona horaria fija `America/Argentina/Buenos_Aires` (no se guarda ubicación del usuario) | Propuesta |
| Rango etario | `ageRange` en vez de edad exacta, por minimización de datos | Propuesta, **coordinar con FRONT-14** |

## 2. Estructura y flujo de una sesión

Estados de una sesión: `GENERATED` → `IN_PROGRESS` → `COMPLETED` o `ABANDONED`.

1. `POST /sessions` (o `POST /assessment/start`) genera la sesión con sus ítems.
2. `POST /sessions/{id}/start` pasa a `IN_PROGRESS`.
3. `POST /sessions/{id}/attempts` registra un intento por ítem (idempotente por `clientAttemptId`).
4. `POST /sessions/{id}/complete` consolida resultados, actualiza perfil de desempeño y niveles, y publica `SessionCompletedEvent`. También existe `POST /sessions/{id}/abandon`.
5. Gamificación y métricas escuchan `SessionCompletedEvent` y los eventos de ciclo de vida. El módulo `session` no las conoce.

Una sesión `GENERATED` o `IN_PROGRESS` con más de 12 h (`mentia.session.stale-after`) se marca `ABANDONED` al pedir una nueva. Una sesión abandonada no actualiza el perfil de desempeño, pero sus intentos quedan en el historial.

## 3. Modelo de datos

Todas las PK son UUID salvo indicación. Timestamps en UTC.

| Tabla | Columnas clave | Ticket |
|---|---|---|
| `users` | `id`, `username` (único, minúsculas), `password_hash`, `recovery_code_hash` (nullable), `created_at` | BACK-01, 03B |
| `revoked_tokens` | `jti` (PK), `expires_at` | BACK-03 |
| `training_profiles` | `user_id` (PK/FK), `age_range`, `education_level` (nullable), `session_minutes`, `goals` (jsonb, códigos de categoría), `onboarding_completed`, `updated_at` | BACK-02 |
| `categories` | `id`, `code` (único), `name`, `description`, `mvp` (bool), `active` | BACK-04, 06 |
| `exercises` | `id`, `code` (único), `name`, `type`, `category_id`, `modality` (VISUAL/AUDITORY), `instructions`, `active`, `post_mvp` | BACK-04 |
| `exercise_levels` | `exercise_id`, `level` (1 a 10), `params` (jsonb), `estimated_seconds`, `target_response_ms` (nullable). PK (`exercise_id`, `level`) | BACK-04 |
| `exercise_variants` | `id`, `exercise_id`, `variant_key`, `content` (jsonb), `active` | BACK-05 |
| `training_sessions` | `id`, `user_id`, `type` (INITIAL_ASSESSMENT/TRAINING), `status`, `planned_minutes`, `rationale` (jsonb), `created_at`, `started_at`, `finished_at`, `result` (jsonb, snapshot) | BACK-12 |
| `session_items` | `id`, `session_id`, `position`, `exercise_id`, `variant_id`, `category_id`, `difficulty_level`, `seed` (bigint), `params` (jsonb, snapshot) | BACK-12 |
| `attempts` | `id`, `client_attempt_id`, `session_item_id` (único), `session_id`, `user_id`, `exercise_id`, `variant_id`, `category_id`, `difficulty_level`, `response` (jsonb), `is_correct`, `accuracy`, `response_time_ms`, `metrics` (jsonb), `created_at`. Único (`user_id`, `client_attempt_id`). Índices (`user_id`, `created_at`) y (`user_id`, `variant_id`, `created_at`) | BACK-12 |
| `performance_profiles` | `user_id`, `category_id`, `score` (0 a 100), `level` (1 a 10), `samples`, `updated_at`. PK (`user_id`, `category_id`) | BACK-08 |
| `user_progress` | `user_id` (PK), `xp`, `level`, `updated_at` | BACK-18 |
| `achievements` | `code` (PK), `name`, `description`, `condition` (jsonb) | BACK-19 |
| `user_achievements` | `user_id`, `achievement_code`, `unlocked_at` | BACK-19 |
| `events` | `id`, `user_id`, `type`, `payload` (jsonb), `occurred_at` | BACK-21 |

La racha y el objetivo diario no tienen tabla: se calculan desde las sesiones completadas.

## 4. Orden de implementación

Cada ola se puede paralelizar internamente. No arrancar una ola sin cerrar las anteriores.

| Ola | Tickets |
|---|---|
| 0 | Bootstrap del repo (sin ticket) |
| 1 | BACK-01, BACK-04 |
| 2 | BACK-03, BACK-02, BACK-03B, BACK-05, BACK-06 |
| 3 | BACK-12, BACK-20 |
| 4 | BACK-07, BACK-11 |
| 5 | BACK-08 |
| 6 | BACK-10 |
| 7 | BACK-09, BACK-13 |
| 8 | BACK-14, BACK-18, BACK-21 |
| 9 | BACK-19 |
| Post-MVP | BACK-15, BACK-16, BACK-17 (no implementar hasta confirmar alcance) |

| Ticket | Jira | Bloqueado por |
|---|---|---|
| BACK-01 Registro | SCRUM-14 | — |
| BACK-02 Perfil de entrenamiento | SCRUM-15 | BACK-01 |
| BACK-03 Autenticación | SCRUM-16 | BACK-01 |
| BACK-03B Recuperación de cuenta | SCRUM-17 | BACK-01 |
| BACK-04 Modelo de ejercicios | SCRUM-18 | — |
| BACK-05 Banco de variantes | SCRUM-19 | BACK-04 |
| BACK-06 Clasificación | SCRUM-20 | BACK-04 |
| BACK-12 Registro de intento | SCRUM-26 | BACK-05 |
| BACK-07 Evaluación inicial | SCRUM-21 | BACK-12 |
| BACK-11 Motor de rotación | SCRUM-25 | BACK-05, BACK-12 |
| BACK-08 Perfil de desempeño | SCRUM-22 | BACK-07 |
| BACK-10 Motor adaptativo | SCRUM-24 | BACK-08, BACK-12 |
| BACK-09 Generación de sesión | SCRUM-23 | BACK-08, BACK-11 |
| BACK-13 Resultado de sesión | SCRUM-27 | BACK-12 (ver ajuste 5) |
| BACK-14 Historial y progreso | SCRUM-28 | BACK-13 |
| BACK-18 Gamificación básica | SCRUM-29 | BACK-13 |
| BACK-19 Logros | SCRUM-30 | BACK-18 |
| BACK-20 Gestión de ejercicios | SCRUM-32 | BACK-05 |
| BACK-21 Métricas internas | SCRUM-31 | BACK-09 |
| BACK-15 Episodios simulados | SCRUM-33 | BACK-04 |
| BACK-16 Recuerdo diferido | SCRUM-34 | BACK-09 |
| BACK-17 Adaptación episódica | SCRUM-35 | BACK-15, BACK-10 |

## 5. Ola 0 — Bootstrap

- Proyecto Spring Boot 3 / Java 21 con paquete `com.mentia` y la estructura de `CLAUDE.md`.
- `docker-compose.yml` con PostgreSQL 16 (servicio `db`).
- `application.yml` con perfiles `dev` y `test`, datasource por variables de entorno, `mentia.security.jwt-secret` desde `MENTIA_JWT_SECRET` y el bloque `mentia.engine` de la sección 6.
- Manejo global de errores con `ProblemDetail` y los códigos del contrato.
- Migración `V1__init.sql` vacía para validar Flyway. Test de arranque con Testcontainers.
- `README.md` con cómo levantar todo.

## 6. Detalle por ticket

### BACK-01 Registro (SCRUM-14)
- `POST /auth/register`. Username de 3 a 30 caracteres `[a-z0-9_.]`, normalizado a minúsculas, único. Contraseña de 10 a 72 caracteres (72 es el límite de BCrypt).
- El servidor genera el UUID. Crea el `training_profile` con el `ageRange` recibido.
- `409 USERNAME_TAKEN` si ya existe. La respuesta incluye `recoveryCode` solo si BACK-03B está implementado (si no, `null`).
- Tests: duplicado sin distinguir mayúsculas, contraseña débil, propiedad desconocida rechazada, el hash nunca es igual a la contraseña.

### BACK-02 Perfil de entrenamiento (SCRUM-15)
- `GET /profile` y `PUT /profile`: `ageRange`, `educationLevel` (opcional), `sessionMinutes` (5 a 30, default 10), `goals` (códigos de categoría, máximo 4), `onboardingCompleted`.
- `assessmentCompleted` es de solo lectura (se deriva de BACK-07).
- `educationLevel` se guarda pero **el motor no lo usa** hasta que la investigación justifique su uso.
- Al pasar `onboardingCompleted` a `true` por primera vez, se registra el evento `onboarding_completed`.

### BACK-03 Autenticación (SCRUM-16)
- `POST /auth/login` y `POST /auth/logout`. JWT con `sub` = userId, `jti` y `exp`.
- Logout inserta el `jti` en `revoked_tokens`. Un job programado limpia los vencidos.
- Credenciales inválidas: siempre `401 INVALID_CREDENTIALS` con el mismo mensaje, exista o no el username.
- Todos los endpoints requieren auth salvo `register`, `login` y `recovery/reset`.

### BACK-03B Recuperación de cuenta (SCRUM-17)
- Código de 20 caracteres de un alfabeto de 32 símbolos (sin ambiguos), formateado en grupos de 5. Se guarda hasheado.
- Se muestra una sola vez: al registrarse y al regenerarlo (`POST /auth/recovery/regenerate`, pide la contraseña actual).
- `POST /auth/recovery/reset`: valida username + código, cambia la contraseña, **rota el código** (devuelve uno nuevo) y revoca los tokens activos.
- Error siempre genérico `400 INVALID_RECOVERY`, exista o no el usuario. Limitar intentos por username.

### BACK-04 Modelo de ejercicios (SCRUM-18)
- Entidades `Category`, `Exercise`, `ExerciseLevel`. Migración y seed de categorías:
  - `ATENCION`, `MEMORIA_VISUAL`, `MEMORIA_TRABAJO` con `mvp = true`.
  - `MEMORIA_AUDITIVA` con `mvp = false` (SHOULD).
  - Memoria asociativa, secuencial y episódica **no se siembran**: se agregan por migración cuando se definan.
- Seed de ejercicios (uno por mecánica del front):

| Mecánica | `type` | Categoría |
|---|---|---|
| Memoria visual (reconocimiento) | `VISUAL_RECOGNITION` | `MEMORIA_VISUAL` |
| Cuadrícula visuoespacial | `SPATIAL_GRID` | `MEMORIA_VISUAL` |
| Elemento diferente | `ODD_ONE_OUT` | `ATENCION` |
| Stroop simplificado | `STROOP` | `ATENCION` |
| Secuencia inversa | `REVERSE_SEQUENCE` | `MEMORIA_TRABAJO` |
| N-back | `N_BACK` | `MEMORIA_TRABAJO` |
| Memoria auditiva | `AUDITORY_SEQUENCE` | `MEMORIA_AUDITIVA` (`active = false` por ahora) |

- `exercise_levels`: niveles 1 a 10 por ejercicio con `params` (ej. longitud de secuencia, tiempo de exposición, distractores). Arrancar con una progresión lineal simple (ej. longitud = 2 + nivel) y ajustar con el equipo.
- Sumar una categoría o ejercicio nuevo no debe requerir tocar código.

### BACK-05 Banco de variantes (SCRUM-19)
- Tabla `exercise_variants` con `content` jsonb (conjunto de estímulos: tema de imágenes, alfabeto, paleta de colores, etc.). Seed de al menos 3 variantes activas por ejercicio activo.
- Interfaz `VariantSelector.pick(userId, exerciseId, level)` que BACK-11 implementa con rotación. En este ticket alcanza con una implementación aleatoria.
- El historial de uso se deriva de `session_items`/`attempts`. No hay tabla extra.

### BACK-06 Clasificación de ejercicios (SCRUM-20)
- `GET /categories` (solo `active`). Filtro por categoría en los servicios internos del catálogo y validación de que un ejercicio activo tenga categoría activa.

### BACK-12 Registro de intento (SCRUM-26)
- Este ticket crea **también** las tablas `training_sessions` y `session_items` y una `SessionFactory` interna mínima, porque BACK-07 y BACK-09 las necesitan. La lógica de generación llega después.
- `POST /sessions/{id}/attempts`: valida que la sesión sea del usuario y esté `IN_PROGRESS`, y que el ítem pertenezca a la sesión. Un intento por ítem.
- Idempotente por `clientAttemptId`: si se repite, responde `200` con el intento original. El primero responde `201`.
- Tests: reintento duplicado, ítem ajeno, sesión en estado inválido, sesión de otro usuario (404).

### BACK-20 Gestión de ejercicios (SCRUM-32)
- Sin endpoints de administración. Documentar en `docs/contenido.md` cómo agregar ejercicios, niveles y variantes con migraciones o seeds Flyway, y cómo revisarlos antes de activarlos (`active = false` hasta revisar).

### BACK-07 Evaluación inicial (SCRUM-21)
- `POST /assessment/start?restart=false`: crea una sesión `INITIAL_ASSESSMENT` con 2 ejercicios por categoría MVP activa, nivel inicial 3, sin rotación ni `rationale`.
- Si ya hay una evaluación `GENERATED` o `IN_PROGRESS`, la devuelve (`200`) para poder retomar. Con `restart=true` abandona la anterior y crea una nueva.
- El cierre usa `SessionService.complete` con una implementación mínima: calcula el nivel inicial por categoría según `accuracy` (tabla de la sección 7) y publica `assessment_completed`. BACK-13 completa el cierre general.
- Sin interpretación médica en ningún texto de respuesta.

### BACK-11 Motor de rotación (SCRUM-25)
- `RotationPolicy` aplicado a ejercicios y variantes. Reglas en la sección 7. Implementa `VariantSelector` de BACK-05.
- Inyectar un `Random` para poder testear de forma determinística.

### BACK-08 Perfil de desempeño (SCRUM-22)
- `PerformanceService.updateAfterSession(...)` con la fórmula de la sección 7.
- `GET /profile/performance`: score (0 a 100) y nivel (1 a 10) por categoría. La descripción del schema aclara que es desempeño dentro de MentIA.

### BACK-10 Motor adaptativo (SCRUM-24)
- `DifficultyEngine.nextLevel(currentLevel, categoryStats)` como función pura con reglas de la sección 7. Todo parametrizado en `mentia.engine`.
- Tests unitarios dirigidos por tabla (casos de borde: nivel mínimo y máximo, pocas muestras, respuesta lenta).
- **Los umbrales de la sección 7 son una propuesta. El ticket pide definirlos y documentarlos antes de implementar: validarlos con el equipo primero.**

### BACK-09 Generación de sesión (SCRUM-23)
- `POST /sessions {durationMinutes}`. Si no hay evaluación inicial completada: `409 ASSESSMENT_REQUIRED`.
- Si ya hay una sesión de entrenamiento `GENERATED` o `IN_PROGRESS` no vencida, la devuelve (`200`).
- Cantidad de ítems: se llena el presupuesto de tiempo sumando `estimated_seconds` del nivel correspondiente.
- Selección de categorías y rotación según sección 7. Nivel de cada ítem = nivel actual del usuario en la categoría.
- Devuelve `rationale`: lista estructurada de motivos con texto en es-AR no clínico (lo necesita FRONT-22).
- `POST /sessions/{id}/start` pasa a `IN_PROGRESS`.

### BACK-13 Resultado de sesión (SCRUM-27)
- `POST /sessions/{id}/complete` consolida: ítems, aciertos, errores, tiempo total, resultados por categoría, nivel alcanzado, adaptaciones (usa `DifficultyEngine`), comparación con la sesión anterior del mismo usuario. Guarda el snapshot en `training_sessions.result`.
- Actualiza `performance_profiles` y publica `SessionCompletedEvent`.
- `POST /sessions/{id}/abandon` y `GET /sessions/{id}/result`.
- La comparación se limita al propio usuario y a desempeño dentro de MentIA.

### BACK-14 Historial y progreso (SCRUM-28)
- `GET /sessions` (paginado), `GET /progress/summary`, `GET /progress/categories?granularity=week|month`.
- Agregaciones en SQL, no en memoria. Revisar índices.

### BACK-18 Gamificación básica (SCRUM-29)
- Escucha `SessionCompletedEvent`. XP, nivel general y, si se confirma, racha y objetivo diario (ver ajuste 4).
- `GET /gamification/me`. Fórmulas en sección 7. Nada de lenguaje clínico.

### BACK-19 Logros (SCRUM-30)
- Catálogo `achievements` sembrado (ej. completar 1, 5 y 10 sesiones, alcanzar nivel 3, completar ejercicios de las 3 categorías). Evaluador que escucha `SessionCompletedEvent`.
- `GET /achievements` con estado de cada uno.

### BACK-21 Métricas internas (SCRUM-31)
- Todos los eventos se registran **del lado servidor** en `events`: `user_registered`, `onboarding_completed`, `assessment_completed`, `session_generated`, `session_started`, `session_completed`, `session_abandoned`, `difficulty_adapted`.
- Migración con vistas SQL (`v_mvp_metrics_*`) para consultar: usuarios registrados, % que completa la evaluación, sesiones iniciadas, completadas y abandonadas, frecuencia de uso, ejercicios completados y adaptaciones. Sin dashboard.
- No hay endpoint de eventos para el cliente (ver ajuste 3).

### BACK-15, 16, 17 Post-MVP
- No implementar. Solo se mantiene el paquete `episodic/` vacío.

## 7. Reglas del motor (propuesta inicial, validar antes de BACK-10)

Configuración en `application.yml`:

```yaml
mentia:
  session:
    stale-after: 12h
  engine:
    initial-level: 3
    level-min: 1
    level-max: 10
    ema-alpha: 0.3              # peso de la sesión nueva en el score
    accuracy-up: 0.85           # subir nivel si accuracy >= este valor
    accuracy-down: 0.60         # bajar nivel si accuracy < este valor
    response-time-tolerance: 1.25   # tiempo promedio <= target * tolerancia para poder subir
    min-items-for-adaptation: 2     # ítems de la categoría en la sesión para ajustar nivel
    selection:
      weak-category-boost: 1.0  # peso = 1 + boost * (100 - score) / 100
    rotation:
      lookback-sessions: 5
      avoid-last-session: true
      recency-weights: [0.0, 0.25, 0.5, 0.75, 1.0]   # sesión anterior ... 5 sesiones atrás
  gamification:
    xp-per-session: 10
    xp-per-item: 2
    xp-accuracy-bonus: 5        # si accuracy de la sesión >= 0.8
    xp-per-level-factor: 100    # pasar del nivel n al n+1 requiere 100 * n XP
```

**Nivel inicial desde la evaluación** (`accuracy` promedio por categoría): `>= 0.90` → 5, `>= 0.75` → 4, `>= 0.60` → 3, `>= 0.40` → 2, resto → 1. El score inicial se calcula como `100 × accuracy`.

**Score de desempeño por categoría:** `scoreSesión = 100 × promedio(accuracy)` de los ítems de esa categoría. `score = score × (1 − α) + scoreSesión × α`.

**Ajuste de nivel por categoría al completar una sesión:**
- Si hay menos de `min-items-for-adaptation` ítems de la categoría → mantener.
- `accuracy >= accuracy-up` y (el ejercicio no tiene `target_response_ms` o el tiempo promedio `<= target × tolerancia`) → subir 1 (tope `level-max`).
- `accuracy < accuracy-down` → bajar 1 (piso `level-min`) y la categoría queda reforzada (mayor peso en la próxima selección).
- En cualquier otro caso → mantener.

**Selección de categorías para una sesión:** primero 1 ítem por categoría MVP activa si caben en el tiempo; el resto por sorteo ponderado con `peso = 1 + boost × (100 − score) / 100`. Las categorías de menor desempeño pesan más, pero ninguna queda excluida.

**Rotación** (aplica a ejercicios dentro de la categoría y a variantes dentro del ejercicio): para cada candidato, ver hace cuántas sesiones se usó por última vez dentro de `lookback-sessions`. Peso = `recency-weights[r − 1]`, o 1.0 si no se usó. Si `avoid-last-session` y hay alternativas, lo usado en la sesión anterior queda en peso 0. Elegir por sorteo ponderado.

**XP:** por sesión completada = `xp-per-session + xp-per-item × ítems + bonus` (si `accuracy >= 0.8`). Subir de nivel requiere `100 × n` XP del nivel n al n+1.

## 8. Ajustes detectados al backlog (confirmar)

1. **`rationale` en la sesión.** FRONT-22 muestra por qué la sesión es así. BACK-09 y BACK-10 deben devolver motivos estructurados con texto no clínico.
2. **Ciclo de vida de sesión.** El front necesita `start` y `abandon` explícitos (FRONT-21). El backlog no los menciona. Quedan incluidos en BACK-09 y BACK-13.
3. **Métricas del lado servidor.** Todo lo que pide BACK-21 se puede registrar en el backend, por lo que FRONT-29 (tracking desde el cliente) puede reducirse o eliminarse.
4. **Racha y objetivo diario.** BACK-18 los menciona como opcionales y FRONT-27 los pide (SHOULD). Confirmar si entran en el MVP.
5. **Vínculo faltante en Jira.** BACK-13 usa el motor de BACK-10, pero Jira solo lo bloquea con BACK-12. El orden de este plan ya lo respeta. Conviene agregar BACK-10 bloquea BACK-13.
6. **Edad.** `ageRange` en el backend frente a "edad" en FRONT-14. Coordinar.
7. **Generación de estímulos con `seed`.** Requiere que el front genere los estímulos de forma determinística. Coordinar antes de BACK-09.

## 9. Testing y definición de terminado

- **Unit:** motor adaptativo, rotación, XP, score. Sin Spring ni base.
- **Integración:** cada endpoint con MockMvc + Testcontainers (PostgreSQL real). Cubrir camino feliz, validaciones, 401/404/409.
- **Contrato:** validar request y response contra `openapi.yaml` (por ejemplo con `swagger-request-validator-mockmvc`).
- **Terminado** significa: migración incluida, endpoint igual al contrato, tests en verde con `./mvnw verify`, sin datos personales ni lenguaje clínico, y README actualizado si se agregó una variable de entorno.

## 10. Cómo trabajar con Claude Code

Un ticket por vez, en el orden de las olas. Prompt sugerido:

```
Implementá SCRUM-14 (BACK-01). Leé CLAUDE.md, la sección BACK-01 de docs/plan-backend.md y openapi.yaml.
Creá la rama feature/SCRUM-14-registro-usuario, incluí migración Flyway y tests, corré ./mvnw verify
y mostrame un resumen de lo hecho antes de commitear.
```

Si conectan el conector de Atlassian en Claude Code, puede leer el ticket directo por su clave y pasarlo a "En curso" / "Listo".
