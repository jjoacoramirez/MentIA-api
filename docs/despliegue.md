# Despliegue: Railway (API + PostgreSQL) y Vercel (UI)

El navegador solo habla con Vercel. Vercel sirve la UI y reenvía `/api/*` a Railway (`vercel.json` del repo de UI), así que todo queda en un mismo origen y no hace falta CORS.

```
navegador ──► Vercel (MentIA-ui) ──/api/*──► Railway: MentIA-api ──► Railway: PostgreSQL
```

Hay que hacer primero Railway, porque Vercel necesita el dominio público de la API.

## 1. Railway

1. Crear un proyecto nuevo con **Deploy from GitHub repo** y elegir `MentIA-api`.
2. En el servicio de la API, en **Settings → Source**, poner la rama **`dev`**: el trabajo integrado está ahí, no en `main`.
3. Agregar la base con **New → Database → PostgreSQL**.
4. En la API, cargar en **Variables**:

   | Variable | Valor |
   |---|---|
   | `MENTIA_DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `MENTIA_DB_USER` | `${{Postgres.PGUSER}}` |
   | `MENTIA_DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `MENTIA_JWT_SECRET` | Un secreto aleatorio de 32 caracteres o más. Usar uno distinto al de desarrollo y no compartirlo. |
   | `MENTIA_AI_ENABLED` | `false` (o `true` con key, ver más abajo) |

   - **Nombre del servicio de la base:** la sintaxis `${{Postgres.X}}` hace referencia a las variables del servicio de la base. Si ese servicio no se llama `Postgres`, se usa su nombre. El autocompletado del editor de variables lo sugiere.
   - **Puerto y perfil:** no hace falta cargar `PORT` (lo pone Railway) ni `SPRING_PROFILES_ACTIVE` (el `Dockerfile` activa `prod`).
5. En **Settings → Networking**, usar **Generate Domain** para obtener el dominio público, del tipo `algo.up.railway.app`.
6. Deploy:
   - Railway construye con el `Dockerfile` (`railway.json`).
   - Al arrancar, Flyway crea todas las tablas.
   - En los logs tiene que aparecer `Started MentiaApiApplication`.
   - Prueba rápida: `https://<dominio>/api/v1/categories` responde **401**, porque falta el token. Eso confirma que la API está viva.

Para generar el secreto del JWT en PowerShell:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 } | ForEach-Object { [byte]$_ }))
```

## 2. Vercel

1. En el repo `MentIA-ui`, reemplazar `TU-DOMINIO.up.railway.app` en `vercel.json` por el dominio de Railway y commitear en `dev`.
2. En Vercel, **Add New → Project → Import** `MentIA-ui`. El framework (Vite), el comando de build y la carpeta `dist` ya vienen de `vercel.json`.
3. No cargar `VITE_API_URL`: por defecto la UI usa `/api/v1`, que Vercel reenvía a Railway.
4. En **Settings → Git → Production Branch**, poner **`dev`**.
5. Deploy y abrir el dominio de Vercel.

## 3. Primera cuenta admin

El panel admin y "Saltear evaluación" necesitan rol ADMIN, y el rol se asigna en la base:

1. Registrarse normalmente en la app publicada.
2. En Railway, entrar a la base y abrir **Data → Query**, o conectarse con la URL pública de la base.
3. Correr:

   ```sql
   UPDATE users SET role = 'ADMIN' WHERE username = 'tu_alias';
   ```
4. Cerrar sesión y volver a entrar: el rol viaja en el token.

Las cuentas ADMIN no cuentan en las métricas del panel (`mentia.metrics.include-admins: false`), así que conviene usarlas solo para probar.

## 4. IA (opcional)

Para activarla, cargar en Railway `MENTIA_AI_ENABLED=true`, `MENTIA_AI_PROVIDER` (`openai` o `anthropic`) y `MENTIA_AI_API_KEY`.
- **Key:** usar una exclusiva de producción.
- **Topes de gasto:** están en `mentia.ai.limits` (US$2 por día y US$20 por mes). Conviene configurar también un límite de gasto en el panel del proveedor.

## Notas

- **Costo:** la API usa unos 300 MB de RAM en reposo y Railway cobra por uso. Conviene revisar el consumo en **Usage** la primera semana.
- **Migraciones:** Flyway las aplica solas en cada deploy. Una migración ya aplicada en producción no se edita nunca; se crea otra.
- **Base de producción:** no hay datos de prueba ni seed de usuarios. Las categorías, ejercicios y variantes sí vienen de las migraciones.
- **Tests:** la imagen no corre los tests, porque necesitan Docker. Se corren con `./mvnw verify` antes de mergear a `dev`.
