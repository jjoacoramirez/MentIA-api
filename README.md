# MentIA-api

Backend del MVP de MentIA (Java 21, Spring Boot 3, PostgreSQL 16). Las reglas están en [CLAUDE.md](CLAUDE.md), el plan en [docs/plan-backend.md](docs/plan-backend.md) y el contrato en [openapi.yaml](openapi.yaml).

## Requisitos

- JDK 21
- Docker (base local y tests con Testcontainers)

## Variables de entorno

Copiar `.env.example` a `.env` y completar. `docker compose` lee `.env` solo; para la API exportar las mismas variables en la terminal o en la configuración de ejecución del IDE.

| Variable | Uso | Default |
|---|---|---|
| `MENTIA_DB_PASSWORD` | Contraseña de PostgreSQL | obligatoria |
| `MENTIA_DB_USER` | Usuario de PostgreSQL | `mentia` |
| `MENTIA_DB_NAME` | Nombre de la base (docker compose) | `mentia` |
| `MENTIA_DB_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/mentia` |
| `MENTIA_JWT_SECRET` | Secreto HS256 del JWT (mínimo 32 caracteres) | obligatoria |
| `ANTHROPIC_API_KEY` | Key de la API de Anthropic (IA). Solo la lee el backend | opcional (vacía = IA deshabilitada) |

`ANTHROPIC_API_KEY` tiene que estar en el entorno de la terminal (o en la run configuration del IDE) desde donde corrés `./mvnw spring-boot:run`: docker compose solo levanta la base y `./mvnw` no lee `.env`. Además de la key hay que activar la IA con `MENTIA_AI_ENABLED=true` (por defecto está apagada y la API funciona igual). Nunca se commitea; usá keys distintas para dev y prod.

## Cómo levantar

```bash
docker compose up -d db      # PostgreSQL local
./mvnw spring-boot:run       # API en localhost:8080 (perfil dev)
./mvnw test                  # tests
./mvnw verify                # tests + chequeos
```

Los tests de integración usan Testcontainers, así que necesitan Docker corriendo. No necesitan `.env`.
**Sin Docker se saltean** (aparecen como `Skipped`) en lugar de fallar: un `verify` en verde sin Docker no valida
la integración con la base, así que antes de mergear hay que correrlo en una máquina con Docker.

Los tests que validan contra el contrato usan `Contract.matchesContract()`, que chequea request y response contra `openapi.yaml`.

## Usuarios admin (debug)

Todo usuario se registra con rol `USER`. El rol `ADMIN` habilita herramientas de debug, como `POST /api/v1/assessment/skip` (saltear la evaluación inicial). Se asigna a mano en la base:

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'mi_usuario';
```

El rol viaja en el JWT, así que el usuario tiene que volver a iniciar sesión para que el cambio tenga efecto. `POST /auth/login` devuelve el `role` para que el front muestre u oculte esas herramientas; los permisos igual los controla el servidor.
