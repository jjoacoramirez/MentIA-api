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

## Cómo levantar

```bash
docker compose up -d db      # PostgreSQL local
./mvnw spring-boot:run       # API en localhost:8080 (perfil dev)
./mvnw test                  # tests
./mvnw verify                # tests + chequeos
```

Los tests usan Testcontainers, así que necesitan Docker corriendo. No necesitan `.env`.
