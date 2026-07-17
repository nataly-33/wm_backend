# GUÍA DE DESPLIEGUE — Plan D (Backup)
## Fly.io (Backend + IA) + MongoDB Atlas + Azure Static Web Apps (Frontend)
## WorkflowManager · Parcial 1 SW1

---

> **Cuándo usar esta guía:** solo si Render falla o da problemas.
> Fly.io es más estable pero requiere instalar su CLI (flyctl).
> El free tier incluye 3 máquinas compartidas gratis — suficiente para backend e IA.
> **No requiere tarjeta de crédito** si te registras con email (no OAuth).

---

## ARQUITECTURA

```
Azure Static Web Apps  → wm_frontend (Angular)
        │ HTTPS
        ▼
Fly.io App             → wm_backend (Spring Boot)
        │                      │
        ▼                      ▼
MongoDB Atlas       Fly.io App → workflow-ia (FastAPI)
```

---

## PASO 0 — Instalar flyctl (CLI de Fly.io)

### Windows (PowerShell como Administrador):

```powershell
iwr https://fly.io/install.ps1 -useb | iex
```

Cerrar y volver a abrir PowerShell. Verificar:

```powershell
fly version
# Debe mostrar: fly v0.x.x ...
```

### Registrarse en Fly.io (sin tarjeta):

```powershell
fly auth signup
```

Se abre el navegador. Registrarse con **email y contraseña** — NO usar "Sign in with GitHub" para que no aparezca en tu GitHub. Verificar el email.

Luego hacer login en la CLI:

```powershell
fly auth login
```

---

## FASE 1 — Deploy del Backend en Fly.io

### 1.1 — Preparar el JAR

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_backend
mvn clean package -DskipTests
```

Verificar que existe: `target\workflow-back-0.0.1-SNAPSHOT.jar`

### 1.2 — Crear Dockerfile en wm_backend

Crear `wm_backend\Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/workflow-back-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx400m", "-Xms200m", "-jar", "app.jar"]
```

### 1.3 — Crear fly.toml en wm_backend

Crear `wm_backend\fly.toml`:

```toml
app = "wm-backend"
primary_region = "mia"

[build]
  dockerfile = "Dockerfile"

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0

[vm]
  memory = "512mb"
  cpu_kind = "shared"
  cpus = 1
```

### 1.4 — Deployar el backend

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_backend
fly launch --no-deploy --name wm-backend --region mia
```

Cuando pregunte si quiere copiar la configuración existente: escribir **Y**.
Cuando pregunte si quiere crear una BD PostgreSQL: escribir **N**.
Cuando pregunte si quiere crear un Redis: escribir **N**.

Luego configurar las variables de entorno:

```powershell
fly secrets set MONGODB_URI="mongodb+srv://wm_admin:TU_PASSWORD@clusterwm.lk7trmf.mongodb.net/workflow_db?retryWrites=true&w=majority" --app wm-backend

fly secrets set JWT_SECRET="workflow-jwt-secret-muy-largo-produccion-2026-cre-santa-cruz" --app wm-backend

fly secrets set JWT_EXPIRATION="86400000" --app wm-backend

fly secrets set IA_SERVICE_URL="https://workflow-ia.fly.dev" --app wm-backend

fly secrets set SPRING_PROFILES_ACTIVE="prod" --app wm-backend
```

Ahora sí deployar:

```powershell
fly deploy --app wm-backend
```

Tarda 4-6 minutos. Los logs aparecen en tiempo real.

### 1.5 — Verificar el backend

```powershell
fly status --app wm-backend
```

Debe mostrar: `wm-backend ... running`

Abrir en el navegador:
```
https://wm-backend.fly.dev/actuator/health
```

Respuesta esperada: `{"status":"UP"}`

Guardar la URL: `https://wm-backend.fly.dev`

---

## FASE 2 — Deploy del Microservicio IA en Fly.io

### 2.1 — Crear Dockerfile en wm_ai

Crear `wm_ai\Dockerfile`:

```dockerfile
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN python -m spacy download es_core_news_sm

COPY . .

# Entrenar el modelo durante el build para que persista en la imagen
RUN python train_model.py

EXPOSE 8001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
```

### 2.2 — Crear fly.toml en wm_ai

Crear `wm_ai\fly.toml`:

```toml
app = "workflow-ia"
primary_region = "mia"

[build]
  dockerfile = "Dockerfile"

[http_service]
  internal_port = 8001
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0

[vm]
  memory = "512mb"
  cpu_kind = "shared"
  cpus = 1
```

### 2.3 — Deployar el microservicio IA

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_ai
fly launch --no-deploy --name workflow-ia --region mia
```

Responder igual que antes: Y, N, N.

Configurar variables:

```powershell
fly secrets set GROQ_API_KEY="gsk_xxxxxxxxxxxxxxxxxxxx" --app workflow-ia
fly secrets set PORT="8001" --app workflow-ia
```

Deployar:

```powershell
fly deploy --app workflow-ia
```

**Este build tarda 15-25 minutos** porque entrena el modelo de ML.
Fly.io muestra los logs en tiempo real — puedes ver el progreso del entrenamiento.

### 2.4 — Verificar la IA

```
https://workflow-ia.fly.dev/health
```

Respuesta esperada: `{"status":"ok","service":"WorkflowManager IA"}`

### 2.5 — Actualizar URL de IA en el backend

```powershell
fly secrets set IA_SERVICE_URL="https://workflow-ia.fly.dev" --app wm-backend
fly deploy --app wm-backend
```

---

## FASE 3 — Frontend en Azure Static Web Apps

Igual que en la guía de Render. Solo cambian las URLs.

### 3.1 — Actualizar environment.prod.ts

```typescript
export const environment = {
  production: true,
  apiUrl: 'https://wm-backend.fly.dev',
  wsUrl: 'wss://wm-backend.fly.dev/ws'
};
```

### 3.2 — Crear staticwebapp.config.json

Crear `wm_frontend\public\staticwebapp.config.json`:

```json
{
  "navigationFallback": {
    "rewrite": "/index.html",
    "exclude": ["/api/*", "/*.{css,js,png,jpg,ico,webp,svg,woff,woff2}"]
  },
  "globalHeaders": {
    "Cache-Control": "no-store"
  }
}
```

### 3.3 — Actualizar CORS en el backend

En `CorsConfig.java` o `SecurityConfig.java`:

```java
config.setAllowedOriginPatterns(List.of(
    "http://localhost:4200",
    "https://*.azurestaticapps.net",
    "https://*.fly.dev"
));
```

Commit + push → `fly deploy --app wm-backend`

### 3.4 — Deploy en Azure Static Web Apps (desde la UI)

1. Portal Azure → **Static Web Apps** → **Create**
2. Configurar:

| Campo | Valor |
|-------|-------|
| Name | `wm-frontend` |
| Plan type | **Free** |
| Region | East US 2 |
| Deployment source | GitHub |
| Repository | wm_frontend |
| Branch | main |
| Build Preset | Angular |
| App location | `/` |
| Output location | `dist/workflow-front` |

3. Create → esperar 3-5 minutos

URL del frontend: `https://agreeable-beach-xxxxx.azurestaticapps.net`

---

## FASE 4 — APK Flutter

### 4.1 — Actualizar URLs

`wm_mobile\lib\core\constants\api_url.dart`:

```dart
class ApiConstants {
  static const String baseUrl = 'https://wm-backend.fly.dev';
  static const String wsUrl   = 'wss://wm-backend.fly.dev/ws';
}
```

### 4.2 — Build y deploy

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_mobile
flutter build apk --release
adb install build\app\outputs\flutter-apk\app-release.apk
```

---

## COMANDOS DE MANTENIMIENTO ÚTILES

```powershell
# Ver estado de las apps
fly status --app wm-backend
fly status --app workflow-ia

# Ver logs en tiempo real
fly logs --app wm-backend
fly logs --app workflow-ia

# Reiniciar una app si no responde
fly machine restart --app wm-backend
fly machine restart --app workflow-ia

# Ver variables de entorno configuradas
fly secrets list --app wm-backend

# Redeploy manual (si hiciste cambios en el código)
fly deploy --app wm-backend
fly deploy --app workflow-ia
```

---

## DIFERENCIAS ENTRE RENDER Y FLY.IO

| Característica | Render | Fly.io |
|----------------|--------|--------|
| Requiere CLI | No | Sí (flyctl) |
| Free tier | Sí | Sí |
| Sleep tras inactividad | Sí (15 min) | Sí (configurable) |
| Tiempo de "despertar" | ~30 seg | ~10 seg |
| Logs en tiempo real | Sí | Sí |
| WebSockets | Sí | Sí |
| Regiones disponibles | Menos | Más (mia = Miami, cerca de Bolivia) |
| Estabilidad | Buena | Muy buena |
| Deploy más rápido | Sí | No (CLI más pasos) |

**Recomendación:** intentar Render primero. Si falla en algo específico, usar esta guía de Fly.io como respaldo.

---

## CHECKLIST FINAL

```
□ fly status --app wm-backend  → running
□ fly status --app workflow-ia → running
□ https://wm-backend.fly.dev/actuator/health → {"status":"UP"}
□ https://workflow-ia.fly.dev/health → {"status":"ok"}
□ https://wm-backend.fly.dev/swagger-ui.html → carga Swagger
□ Frontend Azure → login funciona
□ Refrescar /admin/politicas → NO da 404
□ Monitor WebSocket → actualiza sin F5
□ APK Flutter → login y tareas funcionan
```

**Abrir todas las URLs 5 minutos antes de la defensa** para despertar las apps.
