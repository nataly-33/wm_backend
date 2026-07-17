# GUÍA DE DESPLIEGUE — Plan C (Final)
## Render (Backend + IA) + MongoDB Atlas + Azure Static Web Apps (Frontend)
## WorkflowManager · Parcial 1 SW1

---

> **Por qué Render:**
> - Free tier real sin tarjeta de crédito
> - Ya tienes cuenta creada
> - Deploy desde GitHub con un click
> - Soporta Java (Spring Boot) y Python (FastAPI) nativamente
> - No aparece en tu repo de GitHub de forma obvia
>
> **Limitación conocida:** las apps gratis se "duermen" tras 15 min sin tráfico.
> Solución: abrir las URLs del backend e IA 2 minutos antes de la defensa.

---

## ARQUITECTURA FINAL

```
[Inge / Usuario]
      │
      ▼
Azure Static Web Apps   → wm_frontend (Angular)   ← URL pública Azure
      │ HTTPS
      ▼
Render Web Service      → wm_backend (Spring Boot) ← URL pública Render
      │                       │
      ▼                       ▼
MongoDB Atlas        Render Web Service → workflow-ia (FastAPI Python)

Firebase → Push notifications Flutter
```

---

## ANTES DE EMPEZAR — Verificar que tienes listo

```
□ Cuenta en Render: https://render.com (ya la tienes)
□ Repos en GitHub: wm_backend, wm_frontend, wm_mobile, wm_ai (deben existir)
□ MongoDB Atlas: connection string disponible
□ wm_backend compila sin errores: mvn clean package -DskipTests
□ wm_frontend compila: ng build --configuration production
□ wm_ai corre localmente: uvicorn main:app --port 8001
```

---

## FASE 1 — Preparar el Backend para Render

### 1.1 — Crear el JAR de producción

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_backend
mvn clean package -DskipTests
```

Verificar que existe: `target\workflow-back-0.0.1-SNAPSHOT.jar`

### 1.2 — Crear Dockerfile en wm_backend

Crear el archivo `wm_backend\Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/workflow-back-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xmx400m", \
  "-Xms200m", \
  "-jar", "app.jar"]
```

Los flags `-Xmx400m -Xms200m` limitan la memoria RAM para que quepa en el free tier de Render (512MB disponibles).

### 1.3 — Commit y push del Dockerfile

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_backend
git add Dockerfile
git add target/workflow-back-0.0.1-SNAPSHOT.jar
git commit -m "chore: agregar Dockerfile y JAR para deploy en Render"
git push origin main
```

**IMPORTANTE:** el JAR debe estar en el repo para que Render lo copie.
Verificar que `.gitignore` NO excluye el archivo JAR específico.
Si en el `.gitignore` hay una línea `*.jar` o `target/`, comentarla temporalmente:

```gitignore
# target/          ← comentar esta línea temporalmente
!target/workflow-back-0.0.1-SNAPSHOT.jar   ← agregar esta excepción
```

---

## FASE 2 — Deploy del Backend en Render

### 2.1 — Crear Web Service en Render

1. Ir a: **https://dashboard.render.com**
2. Click **"New +"** → **"Web Service"**
3. Seleccionar: **"Build and deploy from a Git repository"**
4. Conectar tu cuenta de GitHub si no está conectada
5. Seleccionar el repo **`wm_backend`**
6. Configurar:

| Campo | Valor |
|-------|-------|
| Name | `wm-backend` |
| Region | Oregon (US West) — el más rápido para Latinoamérica |
| Branch | `main` |
| Runtime | **Docker** |
| Instance Type | **Free** |

### 2.2 — Variables de entorno en Render

En la sección **"Environment Variables"** (antes de hacer click en Deploy):

```
MONGODB_URI          = mongodb+srv://wm_admin:TU_PASSWORD@clusterwm.lk7trmf.mongodb.net/workflow_db?retryWrites=true&w=majority
JWT_SECRET           = workflow-jwt-secret-muy-largo-produccion-2026-cre-santa-cruz-al-menos-64-chars
JWT_EXPIRATION       = 86400000
IA_SERVICE_URL       = https://workflow-ia.onrender.com
SPRING_PROFILES_ACTIVE = prod
PORT                 = 8080
```

**Nota:** `IA_SERVICE_URL` la actualizas después de desplegar la IA. Por ahora déjala así — el backend funciona sin la IA, solo el análisis y generación de diagrama darán error temporal.

### 2.3 — Click "Create Web Service"

Render hace el build del Docker y despliega. Tarda **5-8 minutos** la primera vez.

Al terminar aparece una URL como:
```
https://wm-backend.onrender.com
```

### 2.4 — Verificar el backend

Abrir en el navegador:
```
https://wm-backend.onrender.com/actuator/health
```

Respuesta esperada:
```json
{"status": "UP"}
```

Si tarda en responder (primera vez): esperar 30 segundos y refrescar. Normal en el free tier.

**Guardar esta URL** — la necesitas para el frontend y la IA.

---

## FASE 3 — Preparar y Deployar el Microservicio IA en Render

### 3.1 — Crear Dockerfile en wm_ai

Crear el archivo `wm_ai\Dockerfile`:

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Dependencias del sistema para compilar scikit-learn y spaCy
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Instalar dependencias Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Descargar modelo spaCy (pequeño, solo para preprocesamiento)
RUN python -m spacy download es_core_news_sm

# Copiar el código de la aplicación
COPY . .

# Entrenar el modelo de ML durante el BUILD (no en runtime)
# Así el modelo .pkl queda dentro de la imagen y no se pierde al reiniciar
RUN python train_model.py

EXPOSE 8001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
```

### 3.2 — Verificar requirements.txt

El archivo `wm_ai\requirements.txt` debe tener:

```
fastapi==0.110.0
uvicorn==0.29.0
spacy==3.7.4
scikit-learn==1.4.2
numpy==1.26.4
pandas==2.2.0
pydantic==2.7.0
python-dotenv==1.0.1
httpx==0.27.0
groq==0.9.0
```

### 3.3 — Verificar .gitignore en wm_ai

El `.gitignore` de wm_ai debe incluir:

```gitignore
venv/
__pycache__/
*.pyc
.env
modelo_cuello_botella.pkl
```

El `.pkl` NO se sube — se genera durante el Docker build en Render.

### 3.4 — Commit y push

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_ai
git add Dockerfile
git add requirements.txt
git add main.py
git add train_model.py
git add app/
git commit -m "chore: agregar Dockerfile para deploy en Render"
git push origin main
```

### 3.5 — Crear Web Service de IA en Render

1. En Render dashboard: **"New +"** → **"Web Service"**
2. Seleccionar repo **`wm_ai`** (o `workflow-ia`)
3. Configurar:

| Campo | Valor |
|-------|-------|
| Name | `workflow-ia` |
| Region | Oregon (US West) |
| Branch | `main` |
| Runtime | **Docker** |
| Instance Type | **Free** |

Variables de entorno:
```
GROQ_API_KEY = gsk_xxxxxxxxxxxxxxxxxxxx
PORT         = 8001
```

Click **"Create Web Service"**.

**El build tarda 10-20 minutos** porque:
- Instala Python + todas las dependencias (scikit-learn, spaCy, etc.)
- Descarga el modelo español de spaCy
- Entrena el modelo de ML (1 millón de muestras — el paso más lento)

Puedes seguir con las otras fases mientras esperas. Render muestra los logs en tiempo real.

### 3.6 — Verificar la IA

```
https://workflow-ia.onrender.com/health
```

Respuesta esperada:
```json
{"status": "ok", "service": "WorkflowManager IA"}
```

### 3.7 — Actualizar IA_SERVICE_URL en el backend

1. En Render dashboard → seleccionar `wm-backend`
2. Ir a **"Environment"**
3. Actualizar:
   ```
   IA_SERVICE_URL = https://workflow-ia.onrender.com
   ```
4. Click **"Save Changes"** → Render redespliega automáticamente (2-3 min)

---

## FASE 4 — Deploy del Frontend en Azure Static Web Apps

### 4.1 — Actualizar environment.prod.ts

En `wm_frontend\src\environments\environment.prod.ts`:

```typescript
export const environment = {
  production: true,
  apiUrl: 'https://wm-backend.onrender.com',
  wsUrl: 'wss://wm-backend.onrender.com/ws'
};
```

**CRÍTICO:** `wss://` (WebSocket seguro) — no `ws://` en producción.

### 4.2 — Actualizar CORS en el backend

En `SecurityConfig.java` o `CorsConfig.java`, agregar los orígenes permitidos:

```java
config.setAllowedOriginPatterns(List.of(
    "http://localhost:4200",
    "https://*.azurestaticapps.net",
    "https://*.onrender.com"
));
```

Hacer commit y push a `wm_backend` — Render redespliega automáticamente.

### 4.3 — Crear staticwebapp.config.json (CRÍTICO para SPA)

Crear archivo `wm_frontend\public\staticwebapp.config.json`:

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

Sin este archivo: refrescar `/admin/politicas` → 404. Es el error más común en SPAs.

### 4.4 — Commit y push del frontend

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_frontend
git add src/environments/environment.prod.ts
git add public/staticwebapp.config.json
git commit -m "chore: actualizar URLs de producción para Render + Koyeb"
git push origin main
```

### 4.5 — Crear Azure Static Web Apps (desde la UI — SIN CLI)

1. Ir a: **https://portal.azure.com**
2. Barra de búsqueda: escribir **"Static Web Apps"**
3. Click **"Create"**
4. Configurar:

| Campo | Valor |
|-------|-------|
| Subscription | Azure subscription 1 |
| Resource Group | rg-workflow |
| Name | wm-frontend |
| Plan type | **Free** |
| Region | East US 2 |
| Deployment source | **GitHub** |
| Organization | tu usuario de GitHub |
| Repository | wm_frontend |
| Branch | main |
| Build Presets | **Angular** |
| App location | `/` |
| Output location | `dist/workflow-front` |

5. Click **"Review + create"** → **"Create"**

Azure crea automáticamente un archivo `.github/workflows/azure-static-web-apps-xxx.yml` en tu repo y hace el primer deploy en 2-3 minutos.

### 4.6 — Obtener la URL del frontend

Cuando termine:
- Ir al recurso `wm-frontend` en Azure Portal
- La URL aparece en la página principal del recurso:
  ```
  https://agreeable-beach-xxxxx.azurestaticapps.net
  ```

### 4.7 — Actualizar CORS en el backend con la URL real

Ahora que tienes la URL real del frontend, actualizar en Render:

```java
config.setAllowedOriginPatterns(List.of(
    "http://localhost:4200",
    "https://agreeable-beach-xxxxx.azurestaticapps.net",  // URL real
    "https://*.onrender.com"
));
```

Commit + push → Render redespliega.

---

## FASE 5 — APK Flutter para producción

### 5.1 — Actualizar URLs en Flutter

En `wm_mobile\lib\core\constants\api_url.dart`:

```dart
class ApiConstants {
  static const String baseUrl = 'https://wm-backend.onrender.com';
  static const String wsUrl   = 'wss://wm-backend.onrender.com/ws';
}
```

### 5.2 — Build APK release

```powershell
cd C:\Users\Invitado\UAGRM\proyecto\workflow\wm_mobile
flutter build apk --release
```

APK en: `build\app\outputs\flutter-apk\app-release.apk`

### 5.3 — Instalar en dispositivo Android

```powershell
adb install build\app\outputs\flutter-apk\app-release.apk
```

O transferir el `.apk` al teléfono por cable/Bluetooth y abrirlo.

---

## FASE 6 — Checklist final antes de la defensa

### URLs a verificar (abrir 5 minutos antes de que llegue Martinez):

```
□ Frontend (Azure):  https://agreeable-beach-xxxxx.azurestaticapps.net
□ Backend (Render):  https://wm-backend.onrender.com/actuator/health → {"status":"UP"}
□ IA (Render):       https://workflow-ia.onrender.com/health → {"status":"ok"}
□ Swagger:           https://wm-backend.onrender.com/swagger-ui.html
```

**Por qué abrir antes:** Render free tier "duerme" las apps tras 15 min sin tráfico.
La primera request tarda ~30 segundos en "despertar" la app. Abriéndolas antes,
cuando Martinez llegue ya están activas y responden instantáneamente.

### Prueba rápida del flujo completo:

```
□ Login con admin@cre.bo / Admin123! → accede como Admin General
□ Políticas → crear nueva con descripción → Generar con IA → diagrama aparece
□ Activar una política existente del seeder
□ Iniciar trámite → funcionario recibe tarea
□ Monitor en tiempo real: completar tarea en otra pestaña → colores cambian sin F5
□ Análisis IA → analizar una política con trámites → aparecen cards con cuellos
□ Flutter APK: login → ver tareas → completar → monitor web actualiza
```

### Si algo no funciona minutos antes de la defensa:

1. **Backend no responde:** ir a Render dashboard → tu app → click "Manual Deploy"
2. **IA no responde:** mismo proceso en la app de IA
3. **Frontend muestra pantalla en blanco:** refrescar con Ctrl+Shift+R (limpiar caché)
4. **WebSocket no conecta:** verificar que usas `wss://` no `ws://` en environment.prod.ts
5. **Login da 401:** verificar que MONGODB_URI en Render variables apunta al Atlas correcto

---

## PARA LA DEFENSA — Qué decirle a Martinez

**Si pregunta dónde está desplegado:**
> "El frontend está en Azure Static Web Apps. El backend y el microservicio de
> Inteligencia Artificial están en Render, que es una plataforma cloud que usa
> contenedores Docker — equivalente a Azure App Service pero más accesible para
> proyectos académicos."

**Si pregunta por la IA:**
> "El microservicio Python usa spaCy para preprocesamiento de texto y Llama 3.1
> via Groq API para entender la semántica del proceso. Para la detección de
> cuellos de botella entrenamos IsolationForest y RandomForestClassifier de
> scikit-learn con un millón de muestras sintéticas. Aquí puede ver las métricas
> del modelo." [mostrar el dashboard de análisis]

---

## TIEMPOS ESTIMADOS

| Tarea | Tiempo |
|-------|--------|
| Preparar Dockerfile backend + push | 5 min |
| Deploy backend en Render | 5-8 min |
| Preparar Dockerfile IA + push | 5 min |
| Deploy IA en Render (incluye entrenamiento) | 15-20 min |
| Actualizar frontend + Azure Static Web Apps | 5-8 min |
| Build APK Flutter | 3-5 min |
| Verificación completa | 10 min |
| **TOTAL** | **48-61 min** |
