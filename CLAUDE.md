# MentIA — Backend

MentIA es una plataforma de entrenamiento cognitivo (memoria, atención y memoria de trabajo) para estudiantes universitarios y jóvenes profesionales de 18 a 30 años. Este repositorio contiene **solo el backend del MVP**.

Documentos que mandan, en este orden:

1. Este archivo: reglas y convenciones.
2. `docs/plan-backend.md`: orden de implementación, modelo de datos y detalle por ticket.
3. `openapi.yaml`: contrato de la API. Si el código y el contrato difieren, se corrige uno de los dos en el mismo PR. Nunca se deja la diferencia.

## Stack

- Java 21, Spring Boot 3.x, Maven (`./mvnw`)
- Spring Web, Spring Data JPA, Bean Validation, Spring Security
- PostgreSQL 16 + Flyway (migraciones en `src/main/resources/db/migration`)
- Auth: JWT (jjwt) + BCrypt
- Tests: JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL)
- Paquete base: `com.mentia`

## Estructura de paquetes

Organizado por funcionalidad, no por capa técnica.

```
com.mentia
├── common/        # manejo de errores (ProblemDetail), config, Clock, utilidades
├── security/      # JWT, filtro, SecurityConfig
├── auth/          # registro, login, logout, recuperación   (BACK-01, 03, 03B)
├── profile/       # perfil de entrenamiento                 (BACK-02)
├── catalog/       # categorías, ejercicios, variantes       (BACK-04, 05, 06, 20)
├── performance/   # evaluación inicial y perfil desempeño   (BACK-07, 08)
├── engine/        # motor adaptativo y rotación             (BACK-10, 11)
├── session/       # generación y ciclo de vida de sesiones  (BACK-09, 13)
├── attempt/       # registro de intentos                    (BACK-12)
├── progress/      # historial y progreso                    (BACK-14)
├── gamification/  # XP, niveles, logros                     (BACK-18, 19)
├── metrics/       # eventos y vistas de métricas del MVP    (BACK-21)
└── episodic/      # Post-MVP, no implementar todavía        (BACK-15, 16, 17)
```

Cada módulo tiene `XController`, `XService`, `XRepository`, `domain/` (entidades JPA) y `dto/` (records). Los módulos se comunican por servicios o por eventos de Spring (`ApplicationEventPublisher`), nunca accediendo a los repositorios de otro módulo.

## Reglas de negocio no negociables

1. **Seudonimización.** Solo se guardan: username, hash de contraseña, UUID interno aleatorio, rango etario y datos de configuración del entrenamiento. Nunca se pide ni se guarda nombre real, apellido, DNI, teléfono, email, dirección, ubicación, fecha exacta de nacimiento ni información médica. Jackson debe rechazar propiedades desconocidas (`spring.jackson.deserialization.fail-on-unknown-properties=true`).
2. **Nada clínico.** Los scores representan desempeño dentro de MentIA, no mediciones de capacidades cognitivas. Está prohibido en código, nombres de campos, mensajes y seeds usar términos como "diagnóstico", "capacidad de memoria", "mejora cognitiva" o "déficit". Nombres como `performance_score` y `training_level` están bien.
3. **Adaptación determinística.** El motor usa reglas, no modelos de IA. Todos los umbrales y pesos viven en `application.yml` bajo `mentia.engine.*`, nunca hardcodeados, y están documentados en `docs/plan-backend.md`.
4. **Extensible por datos.** Agregar categorías, ejercicios, niveles o variantes se hace con filas (migración o seed), sin cambiar la arquitectura.
5. **Comparar al usuario solo consigo mismo.** No hay rankings ni comparaciones entre usuarios.
6. **Secretos.** Contraseñas y códigos de recuperación se guardan siempre hasheados con BCrypt. Nunca van a logs ni a respuestas, salvo el código de recuperación, que se muestra una sola vez. El secreto del JWT viene de la variable de entorno `MENTIA_JWT_SECRET`.

## Convenciones

- Código e identificadores en inglés. Mensajes al usuario, documentación y commits en español (es-AR).
- API bajo `/api/v1`. IDs UUID. Fechas en UTC (`Instant`).
- Errores con `ProblemDetail` (RFC 7807) y un campo `code` estable (ver `openapi.yaml`).
- Los controllers solo usan DTOs (records), nunca entidades. Las transacciones van en la capa de servicio.
- Validación con Jakarta Validation en los DTOs de entrada.
- Migraciones Flyway `V{n}__descripcion.sql`. Una migración ya aplicada no se edita: se crea otra.
- Todo ticket incluye tests. La lógica pura (motor, rotación, XP) se testea con tests unitarios. Los endpoints se testean con MockMvc + Testcontainers.

## Flujo de trabajo por ticket

1. Leer el ticket (clave `SCRUM-xx`) y la sección correspondiente de `docs/plan-backend.md`. Respetar el orden de olas y las dependencias del plan.
2. Crear la rama `feature/SCRUM-xx-descripcion-corta`.
3. Implementar: migración, entidades, servicio, controller, tests. Los endpoints deben coincidir con `openapi.yaml`.
4. Correr `./mvnw verify`. Tiene que pasar completo.
5. Commits con formato `feat(auth): registro de usuario [SCRUM-14]`.
6. Pasar el ticket a "En curso" al empezar y a "Listo" al terminar, solo cuando hay trabajo real. No se cambian estados para aparentar avance.

## Comandos

```bash
docker compose up -d db      # PostgreSQL local
./mvnw spring-boot:run       # levantar la API en localhost:8080
./mvnw test                  # tests
./mvnw verify                # tests + chequeos
```

## Qué no hacer

- No agregar endpoints que no estén en `openapi.yaml` sin actualizarlo.
- No pedir ni guardar datos personales extra "por las dudas".
- No implementar nada de `episodic/` (Post-MVP) hasta que el alcance se confirme.
- No commitear a `main` directamente.
- No hardcodear umbrales del motor ni secretos.
