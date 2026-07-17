# Guía de Funcionalidades Clave — WorkflowManager

Explica cómo se implementaron las funcionalidades más complejas del sistema: qué tecnología usa, qué archivos la implementan y qué tocar si hay que modificarla.

---

## 1. Notificaciones Push (Firebase Cloud Messaging)

### Cómo funciona

```
Funcionario ejecuta tarea
         ↓
Backend Java: MotorWorkflowService.java
avanza el trámite al siguiente nodo
         ↓
NotificacionService.java
crea una Notificacion en MongoDB
         ↓
PushNotificacionService.java
llama a Firebase Admin SDK con el FCM token
del usuario asignado al siguiente nodo
         ↓
Firebase Cloud → Dispositivo Android
         ↓
NotificationService.dart (Flutter)
muestra la notificación
```

### Archivos involucrados

**Backend Java:**
| Archivo | Qué hace |
|---------|----------|
| `wm_backend/src/.../notificacion/service/PushNotificacionService.java` | Envía el push a Firebase. Usa Firebase Admin SDK. Si Firebase no está configurado, loguea warning y continúa sin error. |
| `wm_backend/src/.../notificacion/service/NotificacionService.java` | Crea la notificación en MongoDB y llama a PushNotificacionService |
| `wm_backend/src/.../config/FirebaseConfig.java` | Lee `FIREBASE_CREDENTIALS` del .env y inicializa Firebase Admin SDK |
| `wm_backend/src/.../tramite/service/MotorWorkflowService.java` | Motor del workflow: cuando completa un nodo, determina quién sigue y dispara la notificación |

**App Flutter:**
| Archivo | Qué hace |
|---------|----------|
| `wm_mobile/lib/core/services/notification_service.dart` | Inicializa FCM, pide permisos, maneja mensajes en foreground/background, navega al abrir |
| `wm_mobile/lib/core/services/auth_service.dart` | Al hacer login registra el FCM token en el backend (`_registrarFcmToken`) |
| `wm_mobile/lib/main.dart` | Llama a `NotificationService.init()` al arrancar la app |

### Problemas que tuvimos y cómo los resolvimos

**Problema 1: La notificación no aparecía en foreground**

FCM en Android por defecto NO muestra notificaciones cuando la app está en primer plano (foreground). La solución fue usar `flutter_local_notifications` para mostrarla manualmente:

```dart
// En notification_service.dart
FirebaseMessaging.onMessage.listen((message) {
  // FCM en foreground → mostrar con plugin local
  if (_localNotificationsReady) {
    _mostrarNotificacionLocal(message);
  }
});
```

**Problema 2: El ícono de notificación era negro en Android**

Android requiere un ícono específico para las notificaciones (fondo transparente, solo silueta blanca). El ícono se llama `ic_notification` y está en:
```
wm_mobile/android/app/src/main/res/
  drawable/ic_notification.png
  drawable-hdpi/ic_notification.png
  drawable-xhdpi/ic_notification.png
  (etc.)
```
Si el ícono no existe o es incorrecto, las notificaciones locales fallan silenciosamente. El FCM de background sigue funcionando igual.

**Problema 3: La app no navegaba al tocar la notificación**

Para navegar desde una notificación cuando la app está en background o cerrada, se necesita un `GlobalKey<NavigatorState>` accesible fuera del árbol de widgets. Lo implementamos con `NavigationService`:

```dart
// navigation_service.dart
class NavigationService {
  static final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();
  static void navigateTo(String route) {
    navigatorKey.currentState?.pushNamed(route);
  }
}
```

**Problema 4: El FCM token expira o rota**

El token FCM puede cambiar. Se maneja con:
```dart
messaging.onTokenRefresh.listen((newToken) {
  onTokenRefresh?.call(newToken);  // callback en main.dart → auth_service → backend
});
```

### Requisitos para que funcione

1. **Backend:** Variable de entorno `FIREBASE_CREDENTIALS` con el JSON de la service account de Firebase (no el `google-services.json`, sino el archivo de administración del proyecto).
2. **Flutter:** Archivo `google-services.json` en `android/app/`. Se obtiene de Firebase Console → tu proyecto → Android.
3. **Flutter:** El usuario debe tener el FCM token guardado en el backend. Esto se hace automáticamente al hacer login.

---

## 2. Generación de Diagramas con IA (Groq + spaCy)

### Cómo funciona

```
Admin escribe (o dicta) la descripción del proceso
         ↓
Frontend Angular: nueva-politica.component.ts
POST /api/v1/ia/generar-diagrama
         ↓
Backend Java: IaController.java → IaService.java
(proxy al microservicio Python)
POST http://[IA_SERVICE_URL]/ia/generar-diagrama
         ↓
Python: diagrama_service.py
1. spaCy analiza el texto en español
2. Groq (Llama 3.1 8B) genera el JSON del diagrama
         ↓
JSON: { nodos: [...], transiciones: [...] }
         ↓
Backend Java convierte el JSON en nodos/transiciones
reales en MongoDB con sus IDs
         ↓
Frontend redirige al editor de diagrama con los nodos ya creados
```

### Archivos involucrados

**Python (wm_ai):**
| Archivo | Qué hace |
|---------|----------|
| `app/services/diagrama_service.py` | Lógica principal. Carga spaCy, llama a Groq, valida el JSON |
| `app/routers/diagrama.py` | Endpoint FastAPI `POST /ia/generar-diagrama` |
| `app/models/schemas.py` | `DiagramaRequest` (descripcion + departamentos) |

**Backend Java (wm_backend):**
| Archivo | Qué hace |
|---------|----------|
| `ia/controller/IaController.java` | Expone los endpoints de IA al frontend |
| `ia/service/IaService.java` | Llama al microservicio Python via HTTP (RestTemplate) |
| `ia/dto/GenerarDiagramaRequest.java` | DTO de la petición |

**Frontend Angular (wm_frontend):**
| Archivo | Qué hace |
|---------|----------|
| `modules/admin/pages/politicas/nueva-politica/nueva-politica.component.ts` | Formulario + barra de progreso + llamada al servicio |
| `core/services/ia.service.ts` | Llama a `/api/v1/ia/generar-diagrama` |

### Qué hace spaCy exactamente

spaCy (`es_core_news_sm`) procesa el texto para extraer:
- Verbos en infinitivo (detectar → tarea "Detectar X")
- Entidades nombradas (nombres de departamentos)
- Conjunciones de condición (si/cuando → nodo DECISION)
- Conjunciones de paralelismo (simultáneamente/mientras → nodo PARALELO)

El resultado del NLP se usa para enriquecer el prompt enviado a Groq, haciéndolo más preciso.

### El SYSTEM_PROMPT de Groq

El prompt del sistema está en `diagrama_service.py` → constante `SYSTEM_PROMPT`. Tiene ~100 líneas y define:
- Estructura exacta del JSON de respuesta
- Tipos de nodo válidos (INICIO, TAREA, DECISION, FIN, PARALELO)
- Reglas de construcción (máximo 15 nodos, nombres únicos, etc.)
- 2 ejemplos completos (con decisión y con fork/join)

**Para cambiar el comportamiento del generador:** modificar `SYSTEM_PROMPT`.

---

## 3. Reconocimiento de Voz (Web Speech API)

### Cómo funciona

No usa ninguna librería externa. Usa la **Web Speech API** nativa del browser, disponible en Chrome y Edge.

```javascript
// Así se inicializa en el componente Angular:
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const recognition = new SpeechRecognition();
recognition.lang = 'es-BO';  // Español Bolivia
recognition.continuous = true;  // No para al silencio
recognition.interimResults = true;  // Muestra texto mientras habla
```

### Dónde está implementado

**Aparece en DOS lugares:**

| Archivo | Para qué |
|---------|----------|
| `wm_frontend/src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.ts` | Dictar la descripción del proceso para que la IA genere el diagrama |
| `wm_frontend/src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.ts` | Dar instrucciones de voz al editor del diagrama |

### Limitaciones conocidas

- Solo funciona en **Chrome y Edge** (no en Firefox, Safari en desktop)
- Requiere permiso de micrófono del browser
- El idioma está en `'es-BO'` (español Bolivia). Si el ingeniero pide cambiarlo: buscar `recognition.lang` en los archivos mencionados.
- La transcripción no es perfecta, especialmente con nombres técnicos

### Cómo funciona la grabación continua

```typescript
// Acumula el texto final y muestra el interim
recognition.onresult = (event) => {
  let textoInterim = '';
  for (let i = event.resultIndex; i < event.results.length; i++) {
    if (event.results[i].isFinal) {
      this.textoAcumulado += event.results[i][0].transcript + ' ';
    } else {
      textoInterim = event.results[i][0].transcript;
    }
  }
  this.form.patchValue({ descripcion: this.textoAcumulado + textoInterim });
};

// Se reinicia solo al terminar (para grabación continua)
recognition.onend = () => {
  if (this.grabando) this.recognition.start();
};
```

---

## 4. Generación de Formularios con IA (Groq)

### Cómo funciona

```
Admin abre el editor de diagrama
→ hace clic en un nodo TAREA
→ clic en "Generar formulario con IA"
         ↓
Frontend: editor-diagrama.component.ts
POST /api/v1/ia/generar-formulario
con el nombre del nodo como descripción
         ↓
Python: formulario_service.py
Groq genera los campos del formulario
         ↓
Backend crea el formulario en MongoDB
y lo asocia al nodo
```

### Archivos involucrados

**Python:**
| Archivo | Qué hace |
|---------|----------|
| `app/services/formulario_service.py` | Llama a Groq con el SYSTEM_PROMPT de formularios |
| `app/routers/formulario.py` | Endpoint `POST /ia/generar-formulario` |

**Tipos de campo generados:** TEXTO, NUMERO, FECHA, SELECCION, IMAGEN, ARCHIVO

**Campo clave:** `es_campo_prioridad: true` → el motor de workflow usa este campo para decidir qué transición tomar (Aprobado/Rechazado).

---

## 5. Análisis de Cuellos de Botella (ML)

### Cómo funciona

```
Admin abre la página /admin/analisis
→ selecciona una política
→ clic en "Analizar"
         ↓
Frontend: analisis-ia.component.ts
POST /api/v1/ia/generar-analisis
         ↓
Backend Java: IaController → IaService
(envía métricas de cada nodo desde MongoDB)
         ↓
Python: analisis_service.py
1. _enriquecer_metricas() → 10 features por nodo
2. StandardScaler normaliza
3. RandomForest + GradientBoosting → probabilidad
4. IsolationForest → ¿anomalía estadística?
5. Ensemble = promedio RF + GB
         ↓
Lista ordenada: mayor probabilidad primero
Con severidad: CRITICA/ALTA/MEDIA/BAJA
Con sugerencias de mejora
```

### Métricas que envía Java al Python

El backend calcula estas métricas por nodo desde `EjecucionNodo` en MongoDB:
- `tiempo_promedio_minutos` — Promedio de duración de todas las ejecuciones
- `cantidad_ejecuciones_activas` — Cuántas están en estado ACTIVA ahora
- `tasa_rechazo` — Rechazadas / (Completadas + Rechazadas)
- `tiempo_espera_promedio_minutos` — Tiempo desde asignación hasta inicio
- `varianza_tiempo` — Desviación estándar de los tiempos

### El modelo ML

- **Versión actual:** `v3_overlap`
- **Archivo:** `modelo_cuello_botella.pkl` (~85 MB)
- **AUC-ROC esperado:** 0.86-0.92 (no 1.0 — eso sería sobreajuste)
- **Accuracy esperado:** 82-91%

Si el `.pkl` es de una versión anterior, se re-entrena automáticamente al iniciar el servidor.

---

## 6. Motor de Workflow

### Cómo funciona

El motor es el corazón del sistema. Cuando un funcionario completa una tarea, el motor decide qué pasa después.

```
Funcionario completa tarea en Flutter/Angular
         ↓
PUT /api/v1/ejecuciones/{id}/completar
         ↓
EjecucionService.java → MotorWorkflowService.java
         ↓
1. Marca la EjecucionNodo como COMPLETADA
2. Lee el campo de prioridad del formulario (Aprobado/Rechazado)
3. Busca la Transicion cuya etiqueta coincide
4. Crea la siguiente EjecucionNodo (asignada al siguiente funcionario)
5. Emite evento WebSocket con el nuevo estado
6. Envía notificación push al siguiente funcionario
         ↓
El trámite avanza automáticamente
```

**Archivo clave:** `wm_backend/src/.../tramite/service/MotorWorkflowService.java`

---

## 7. WebSockets en Tiempo Real

### Tecnologías

- **Backend:** Spring WebSocket + STOMP (`WebSocketConfig.java`)
- **Frontend Angular:** `@stomp/stompjs` + `sockjs-client` (`socket.service.ts`)
- **App Flutter:** `socket_io_client` (`socket_service.dart`)

### Qué eventos se emiten

| Evento | Cuándo | Quién lo escucha |
|--------|--------|-----------------|
| `nodoActualizado` | Cuando un nodo cambia de estado en un trámite | Monitor (Angular + Flutter) |
| `nuevaTarea` | Cuando se asigna una nueva tarea | Funcionario (Angular) |
| `tramiteCompletado` | Cuando termina el proceso completo | Monitor (Angular) |

### Dónde configurar la URL del WebSocket

- **Angular:** `src/environments/environment.ts` → `wsUrl: 'http://localhost:8080/ws'`
- **Flutter:** `lib/core/constants/api_url.dart` → se construye desde `baseUrl`

---

## 8. Almacenamiento de Archivos (Azure Blob Storage)

Los archivos adjuntos subidos en los formularios (documentos, imágenes) se almacenan en Azure Blob Storage, no en MongoDB.

**Flujo:**
```
Funcionario sube archivo en el formulario (Flutter/Angular)
         ↓
POST /api/v1/archivos/upload
         ↓
ArchivoService.java → Azure Blob Storage SDK
         ↓
Retorna URL pública del archivo
→ Se guarda la URL en la respuesta del formulario en MongoDB
```

**Archivo clave:** `wm_backend/src/.../archivo/service/ArchivoService.java`

**Variable de entorno:** `AZURE_STORAGE_CONNECTION_STRING` en el `.env` del backend.

> Si no está configurado Azure, la subida de archivos falla. En desarrollo local se puede omitir esta variable y los campos ARCHIVO/IMAGEN no funcionarán.
