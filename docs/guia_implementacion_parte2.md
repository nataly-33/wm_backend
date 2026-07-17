# GUÍA DE IMPLEMENTACIÓN — PARTE 2
## Plan detallado desde el Día 7 en adelante
### WorkflowManager · Parcial 1 SW1

---

> **Cómo usar esta guía:**
> NO incluye código, solo instrucciones, archivos y endpoints.

---

## DÍA 7 — Push Notifications Flutter + Vistas Mobile (CU-11)

### 7.1 — Backend: Firebase para push notifications

**Archivo:** `wm_backend/pom.xml`
Dependencia ya debe existir: `com.google.firebase:firebase-admin:9.2.0`

**Archivo nuevo:** `wm_backend/.../config/FirebaseConfig.java`

```
Configuración:
- Leer la variable de entorno FIREBASE_CREDENTIALS (JSON base64 o path al archivo)
- Inicializar FirebaseApp con las credenciales
- Exponer como @Bean singleton
- Si la variable está vacía → logear warning "Firebase no configurado, push deshabilitado"
  y NO lanzar excepción (el sistema funciona sin push en desarrollo local)
```

**Archivo nuevo:** `wm_backend/.../notificacion/service/PushNotificacionService.java`

```
Métodos:
- enviarPush(String fcmToken, String titulo, String cuerpo, Map<String,String> data)
  → Si fcmToken es null o vacío → skip silencioso (no error)
  → Si Firebase no está configurado → skip silencioso
  → Construir mensaje FCM con notification + data payload
  → Enviar con FirebaseMessaging.getInstance().send(message)
  → Loggear éxito o error con SLF4J

- enviarPushATodos(List<String> fcmTokens, String titulo, String cuerpo, Map<String,String> data)
  → Llama a enviarPush por cada token válido
  → Útil para notificar a todos los funcionarios de un departamento

CUÁNDO SE LLAMA (desde MotorWorkflowService):
- Al crear nueva EjecucionNodo en un departamento:
    → Obtener todos los usuarios del departamento (admin + funcionarios) que tienen fcm_token
    → enviarPushATodos(...) con título "Nueva tarea: {nombre nodo}"
    → Data: { tipo: "ASIGNACION", ejecucionId: "...", tramiteId: "..." }

- Al completar un trámite (estado = COMPLETADO):
    → Obtener el Admin General de la empresa
    → enviarPush(...) con título "Trámite completado: {título}"
    → Data: { tipo: "COMPLETADO", tramiteId: "..." }

- Al rechazar un trámite:
    → Obtener el Admin General de la empresa
    → enviarPush(...) con título "Trámite rechazado: {título}"
    → Data: { tipo: "RECHAZADO", tramiteId: "..." }
```

**Endpoint nuevo:** `PUT /usuarios/{id}/fcm-token`
```
Body: { fcmToken: "string" }
Descripción: Guarda el token FCM del dispositivo del usuario
Rol: cualquier usuario autenticado puede actualizar su propio token
Validación: el id del path debe coincidir con el userId del JWT (no puedes actualizar el token de otro)
```

---

### 7.2 — Flutter: configuración Firebase y notificaciones

**Archivo:** `wm_mobile/android/app/google-services.json`
→ Debe estar en .gitignore. El desarrollador lo agrega manualmente.

**Archivo:** `wm_mobile/lib/core/services/notification_service.dart`

```
Clase: NotificationService

Método estático init():
  - Solicitar permisos de notificación al SO
  - Inicializar FlutterLocalNotificationsPlugin para foreground
  - Crear canal de notificaciones Android: id="workflow_channel", nombre="WorkflowManager",
    importancia HIGH
  - Registrar handler para mensajes en FOREGROUND:
    FirebaseMessaging.onMessage.listen() → mostrar notificación local con flutter_local_notifications
  - Registrar handler para tap en notificación con app en BACKGROUND:
    FirebaseMessaging.onMessageOpenedApp.listen() → navegar según data.tipo

Método estático obtenerToken() → Future<String?>:
  - FirebaseMessaging.instance.getToken()
  - Retorna el FCM token o null si falla

Método privado _navegarSegunTipo(Map<String,String> data):
  - Si data['tipo'] == 'ASIGNACION' → navegar a /tareas
  - Si data['tipo'] == 'COMPLETADO' → navegar a /monitor
  - Si data['tipo'] == 'RECHAZADO' → navegar a /monitor
```

**Archivo:** `wm_mobile/lib/core/services/auth_service.dart`

```
Agregar al método login() DESPUÉS de guardar el token JWT:
  1. Llamar a NotificationService.obtenerToken()
  2. Si el token no es null → PUT /usuarios/{userId}/fcm-token
  3. No bloquear el login si esto falla (try/catch silencioso)
```

---

### 7.3 — Flutter: pantallas del funcionario

**Archivo:** `wm_mobile/lib/screens/funcionario/tareas_screen.dart`

```
Estado: isLoading, error, tareas List<EjecucionNodo>

Al iniciar:
  - GET /ejecuciones/funcionario/{userId}
  - Ordenar por: primero ALTA prioridad, luego MEDIA, luego BAJA
  - Dentro de cada prioridad, ordenar por fecha_limite ASC (más urgente primero)

Cada card de tarea muestra:
  - Título del trámite
  - Nombre del nodo (la tarea específica)
  - Chip de prioridad: ALTA=rojo, MEDIA=amarillo, BAJA=verde
  - Fecha límite (si existe) — formateada como "Vence: 20/04/2026"
  - Estado: solo mostrar PENDIENTE y EN_PROCESO (no mostrar las completadas)

Al tocar una card → navegar a EjecutarTareaScreen con ejecucionId

Pull to refresh: RefreshIndicator → recargar GET /ejecuciones/funcionario/{userId}

Badge en el ícono: contar tareas con estado PENDIENTE
```

**Archivo:** `wm_mobile/lib/screens/funcionario/ejecutar_tarea_screen.dart`

```
Recibe: ejecucionId (String)

Al iniciar:
  1. GET /ejecuciones/{ejecucionId} → obtener detalles de la ejecución
  2. GET /formularios/nodo/{nodoId} → obtener el formulario del nodo
  3. Si no hay formulario → mostrar mensaje "Esta tarea no requiere formulario"
     y mostrar solo los botones Completar/Rechazar

Construcción dinámica del formulario:
  Para cada campo en formulario.campos:
    - tipo TEXTO → TextField con validación de requerido
    - tipo NUMERO → TextField con keyboardType=number, validación numérica
    - tipo FECHA → InkWell que abre showDatePicker, mostrar fecha seleccionada
    - tipo SELECCION → DropdownButtonFormField con lista de opciones
    - tipo IMAGEN → botón "📷 Tomar foto o seleccionar" → ImagePicker
      → subir a: POST /archivos/subir (endpoint del back que sube a Azure Blob)
      → guardar URL retornada
    - tipo ARCHIVO → botón "📎 Seleccionar archivo" → FilePicker
      → mismo flujo de subida que IMAGEN

Botón "Completar":
  1. Validar campos requeridos
  2. Si hay errores → mostrar en rojo debajo de cada campo
  3. Si todo válido → PUT /ejecuciones/{id}/completar
     Body: { respuestaFormulario: { campo1: valor1, campo2: valor2, ... } }
  4. Mostrar CircularProgressIndicator mientras carga
  5. Al completar → SnackBar "Tarea completada" → volver a TareasScreen

Botón "Rechazar":
  1. Mostrar diálogo con TextField para observaciones (requerido)
  2. PUT /ejecuciones/{id}/rechazar
     Body: { observaciones: "texto del usuario" }
  3. Al rechazar → SnackBar "Tarea rechazada" → volver a TareasScreen

IMPORTANTE — Actualizar estado a EN_PROCESO:
  Al abrir la pantalla, si el estado es PENDIENTE:
  → PUT /ejecuciones/{id}/iniciar (endpoint nuevo en el back)
  → Esto cambia el estado a EN_PROCESO y guarda iniciado_en
```

**Endpoint nuevo en backend:** `PUT /ejecuciones/{id}/iniciar`
```
Descripción: Cambia estado PENDIENTE → EN_PROCESO, guarda iniciado_en = now()
Rol: FUNCIONARIO o ADMIN_DEPARTAMENTO del departamento correspondiente
Emite evento WebSocket al monitor: { tramiteId, nodoId, estado: "EN_PROCESO" }
```

---

### 7.4 — Flutter: pantalla del monitor (Admin)

**Archivo:** `wm_mobile/lib/screens/admin/monitor_screen.dart`

```
Recibe: politicaId (String)

Actualiza e imita el monitor en tiempo real con websockets funcional de la vista de administrador que ya esta en el front de Angular, revisa los archivos 

```

---

## DÍA 8 — IA: Generación de diagrama con NLP (CU-12)

### Arquitectura del microservicio IA

**Repositorio:** `workflow-ia/` (puede estar dentro de wm_backend como carpeta separada)

**Estructura de archivos:**
```
workflow-ia/
├── main.py                          → FastAPI app
├── train_model.py                   → Script de entrenamiento (ejecutar 1 vez)
├── modelo_cuello_botella.pkl        → Modelo entrenado (generado por train_model.py)
├── requirements.txt
├── .env
├── app/
│   ├── routers/
│   │   ├── diagrama.py              → POST /ia/generar-diagrama
│   │   ├── analisis.py              → POST /ia/analizar-politica
│   │   └── formulario.py           → POST /ia/generar-formulario
│   ├── services/
│   │   ├── diagrama_service.py      → Pipeline NLP con spaCy
│   │   ├── analisis_service.py      → IsolationForest + RandomForest
│   │   └── formulario_service.py    → Generación de campos
│   └── models/
│       └── schemas.py               → Pydantic models
└── Dockerfile
```

**Archivo:** `workflow-ia/requirements.txt`
```
fastapi==0.110.0
uvicorn==0.29.0
spacy==3.7.4
scikit-learn==1.4.0
numpy==1.26.4
pandas==2.2.0
pydantic==2.7.0
python-dotenv==1.0.1
httpx==0.27.0
SpeechRecognition==3.10.1
```

**Después de instalar:** `python -m spacy download es_core_news_md`

---

### 8.1 — Pipeline NLP para generación de diagramas

**Archivo:** `workflow-ia/app/services/diagrama_service.py`

El servicio NLP realiza estas etapas en orden:

```
ETAPA 1 — Preprocesamiento:
  - Recibir el texto del usuario
  - Normalizar: minúsculas, quitar tildes opcionales, expandir abreviaciones comunes
  - Procesar con spaCy: nlp = spacy.load("es_core_news_md"); doc = nlp(texto)

ETAPA 2 — Extracción de departamentos:
  - Buscar entidades NER de tipo ORG o PER en el doc
  - Buscar sustantivos que coincidan con lista de departamentos de la empresa
    (la lista se recibe en el request: departamentos_empresa: List[str])
  - Si el usuario proveyó departamentos en el request → priorizar esos
  - Si no → usar los detectados por NLP
  - Eliminar duplicados, mantener orden de aparición

ETAPA 3 — Extracción de acciones por departamento:
  - Para cada oración en doc.sents:
    - Identificar el departamento al que se refiere (por proximidad textual)
    - Extraer el verbo principal de la oración (token.pos_ == "VERB" y token.dep_ == "ROOT")
    - Construir la acción: verbo + objeto directo si existe
  - Agrupar acciones por departamento

ETAPA 4 — Detección de condiciones/decisiones:
  - Buscar patrones: "si", "cuando", "en caso de", "dependiendo"
  - Extraer la condición y sus ramas
  - Detectar palabras de negación para identificar la rama negativa

ETAPA 5 — Detección de paralelismo:
  - Buscar: "simultáneamente", "en paralelo", "al mismo tiempo", "mientras"
  - Marcar los nodos que pueden ejecutarse en paralelo

ETAPA 6 — Construcción del diagrama UML 2.5:
  - Crear nodo INICIO (Initial Node)
  - Por cada departamento en orden: crear nodo TAREA con la acción principal
  - Si hay condición: insertar nodo DECISION antes de la bifurcación
  - Si hay paralelismo: insertar FORK y JOIN
  - Crear nodo FIN (Activity Final)
  - Crear transiciones:
    - LINEAL entre nodos en secuencia
    - ALTERNATIVA desde DECISION con etiquetas [Aprobado]/[Rechazado]
    - PARALELA desde FORK hacia nodos paralelos
  - Calcular posiciones x,y en cuadrícula:
    - x: 150px + (índice_columna * 250px) para swimlanes verticales
    - y: 80px + (índice_fila * 150px) dentro de cada carril

ETAPA 7 — Retornar JSON:
  - Estructura idéntica a lo que esperaría PUT /politicas/{id}/diagrama
  - nodos[] con tipo, nombre, departamentoId (se mapea por nombre de departamento)
  - transiciones[] con tipo, etiqueta, condicion
```

---

### 8.2 — Entrada por voz

**Archivo:** `workflow-ia/app/routers/diagrama.py`

```
Endpoint: POST /ia/generar-diagrama
Body puede ser:
  a) JSON: { prompt: "texto", departamentos: [...], politicaId: "..." }
  b) Multipart form: { audio: File, departamentos: [...], politicaId: "..." }

Si viene audio (field "audio" presente):
  1. Guardar el archivo de audio temporalmente
  2. Usar SpeechRecognition con Google Speech API (gratuita hasta cierto límite):
     - recognizer = sr.Recognizer()
     - with sr.AudioFile(audio_path) as source: audio = recognizer.record(source)
     - texto = recognizer.recognize_google(audio, language='es-BO')
  3. Usar ese texto como prompt para el pipeline NLP
  4. Eliminar el archivo temporal

Si viene texto (field "prompt" presente):
  → Usar directamente el pipeline NLP

Respuesta en ambos casos:
  {
    "nodos": [...],
    "transiciones": [...],
    "texto_transcrito": "..." (solo si fue por voz, para confirmación)
  }
```

---

### 8.3 — Frontend: botón "Generar con IA" en el editor

**Archivo:** `wm_frontend/.../editor-diagrama/editor-diagrama.component.ts`

```
Botón "✨ Generar con IA":
  → Abrir modal/dialog GenerarConIaDialog

Modal GenerarConIaDialog contiene:
  - Tabs: [📝 Texto] [🎤 Voz]
  
  Tab Texto:
    - Textarea: "Describe el proceso en lenguaje natural..."
    - Textarea muestra sugerencia de ejemplo:
      "Proceso de instalación de medidor: atención al cliente recibe la solicitud,
       el técnico verifica si es viable, si es viable facturación cobra el pago,
       si no se rechaza y se notifica al cliente"
    - Botón "Generar diagrama"

  Tab Voz:
    - Botón "🎤 Grabar" → activa el micrófono del navegador
    - Usar MediaRecorder API del navegador para grabar audio
    - Mostrar indicador de grabación (ondas animadas)
    - Botón "⏹ Detener"
    - Mostrar preview del audio grabado
    - Botón "🚀 Enviar y generar"

Al generar (por texto o por voz):
  1. Mostrar spinner "Analizando con IA..."
  2. Construir el request:
     - Si texto: POST /ia/generar-diagrama con JSON
     - Si voz: POST /ia/generar-diagrama con FormData (audio como File)
     Incluir: departamentos = lista de departamentos de la empresa (IDs y nombres)
  3. Al recibir respuesta:
     - Si vino de voz: mostrar el texto transcrito en un toast para confirmación
     - Limpiar el canvas actual (confirmar si ya tiene nodos)
     - Cargar los nodos y transiciones en el canvas de mxGraph
     - Los nodos se convierten en vértices con el estilo UML correspondiente
     - Las transiciones se convierten en aristas
     - Marcar diagrama como modificado=true
  4. Toast: "Diagrama generado. Puedes editarlo antes de guardar."

IMPORTANTE: el diagrama generado por IA es 100% editable igual que uno manual.
No hacer nada diferente después de generarlo — el usuario puede mover, editar, eliminar.
```

---

### 8.4 — Backend proxy para IA

**Archivo:** `wm_backend/.../ia/IaController.java`

```
Endpoint: POST /ia/generar-diagrama
Rol requerido: ADMIN_GENERAL
Descripción:
  1. Recibir el request del frontend (JSON o multipart con audio)
  2. Hacer request HTTP al microservicio Python: POST {IA_SERVICE_URL}/ia/generar-diagrama
  3. Si la respuesta tiene nodos y transiciones:
     → NO guardar automáticamente en BD (el usuario editará antes de guardar)
     → Retornar directamente la respuesta del microservicio al front
  4. Si el microservicio IA no está disponible (timeout o error):
     → Retornar 503 con mensaje "El servicio de IA no está disponible temporalmente"
     → No lanzar 500 (el error de IA no debe tumbar el sistema principal)
```

---

## DÍA 9 — IA: Formularios + Análisis de cuellos de botella (CU-13, CU-14)

### 9.1 — Generación de campos de formulario con IA

**Archivo:** `workflow-ia/app/services/formulario_service.py`

```
Función: generar_campos_formulario(descripcion_nodo: str) -> List[dict]

Pipeline:
  1. Procesar texto con spaCy
  2. Identificar entidades que deben capturarse (personas, fechas, documentos, cantidades)
  3. Para cada entidad detectada, determinar el tipo de campo:
     - Si menciona "fecha", "plazo", "vencimiento" → tipo FECHA
     - Si menciona "imagen", "foto", "fotografía" → tipo IMAGEN
     - Si menciona "archivo", "documento", "adjunto" → tipo ARCHIVO
     - Si menciona "número", "cantidad", "monto", "precio" → tipo NUMERO
     - Si menciona opciones específicas ("aprobado/rechazado", "sí/no") → tipo SELECCION
     - Por defecto → tipo TEXTO
  4. Generar nombre técnico del campo (snake_case sin tildes)
  5. Generar etiqueta legible para el usuario
  6. Determinar si es requerido (si se menciona "obligatorio", "necesario", "debe")
  7. Retornar lista de campos

Ejemplo:
  Input: "Formulario para verificar si el medidor es apto. Capturar foto del medidor,
          fecha de inspección, resultado (apto/no apto) y observaciones"
  Output:
    [
      { nombre: "foto_medidor", etiqueta: "Foto del medidor", tipo: "IMAGEN", requerido: true },
      { nombre: "fecha_inspeccion", etiqueta: "Fecha de inspección", tipo: "FECHA", requerido: true },
      { nombre: "resultado", etiqueta: "Resultado", tipo: "SELECCION",
        opciones: ["Apto", "No apto"], requerido: true, es_campo_prioridad: true },
      { nombre: "observaciones", etiqueta: "Observaciones", tipo: "TEXTO", requerido: false }
    ]
```

---

### 9.2 — Modelo analítico de detección de cuellos de botella

**Archivo:** `workflow-ia/app/services/analisis_service.py`

El modelo usa `scikit-learn` (no una API externa).

```
DATOS DE ENTRENAMIENTO — Generar datos sintéticos:

Función: generar_datos_sinteticos(n_samples=2000) -> DataFrame

Columnas del DataFrame:
  - tiempo_promedio_minutos: float (tiempo promedio que tarda un nodo en completarse)
  - cantidad_ejecuciones: int (cuántas ejecuciones activas tiene ese nodo)
  - tasa_rechazo: float (0.0 a 1.0, proporción de rechazos)
  - tiempo_espera_promedio: float (tiempo que pasa entre que se crea y se empieza)
  - varianza_tiempo: float (qué tan inconsistente es el tiempo de ejecución)
  - es_cuello_botella: int (0 o 1 — etiqueta para entrenamiento supervisado)

Distribuciones para generación sintética:
  Nodos normales (80% de los datos):
    - tiempo_promedio_minutos: Normal(media=45, std=15) → aprox 30-60 min
    - cantidad_ejecuciones: Uniform(1, 8)
    - tasa_rechazo: Uniform(0.0, 0.1)
    - es_cuello_botella: 0

  Nodos cuello de botella (20% de los datos):
    - tiempo_promedio_minutos: Normal(media=480, std=120) → aprox 4-8 horas
    - cantidad_ejecuciones: Uniform(15, 50)
    - tasa_rechazo: Uniform(0.2, 0.6)
    - es_cuello_botella: 1

MODELOS A ENTRENAR:

Modelo 1 — IsolationForest (no supervisado, detección de anomalías):
  - contamination=0.1 (esperamos ~10% de cuellos de botella)
  - n_estimators=100
  - random_state=42
  - Uso: detectar nodos con comportamiento estadísticamente atípico

Modelo 2 — RandomForestClassifier (supervisado, clasificación):
  - n_estimators=100
  - max_depth=5
  - random_state=42
  - Uso: clasificar con probabilidad si es cuello de botella

Ambos modelos se entrenan, se evalúan (train/test split 80/20), y se guardan en
modelo_cuello_botella.pkl con pickle.

SCRIPT DE ENTRENAMIENTO: train_model.py
  - Genera 2000 muestras sintéticas
  - Hace split 80/20
  - Entrena ambos modelos
  - Imprime métricas: accuracy, precision, recall, F1, confusion matrix
  - Guarda el modelo en disco
  - DEBE ejecutarse antes de iniciar el servidor por primera vez

CARGA DEL MODELO EN EL SERVIDOR:
  - Al iniciar FastAPI, cargar el modelo desde disco con pickle.load()
  - Si el archivo no existe → ejecutar entrenamiento automáticamente
  - Usar un singleton (variable global) para no recargar en cada request

FUNCIÓN PRINCIPAL: predecir(ejecuciones: List[dict]) -> List[dict]

Input (ejecuciones calculadas por el backend antes de llamar al microservicio):
  [
    {
      "nodo_id": "...",
      "nombre_nodo": "Verificar documentación",
      "tiempo_promedio_minutos": 480.5,
      "cantidad_ejecuciones": 23,
      "tasa_rechazo": 0.35,
      "tiempo_espera_promedio": 120.0,
      "varianza_tiempo": 200.0
    }
  ]

Output:
  [
    {
      "nodo_id": "...",
      "nombre_nodo": "Verificar documentación",
      "es_cuello_botella": true,
      "probabilidad_cuello": 0.87,
      "severidad": "ALTA",  // BAJA | MEDIA | ALTA | CRITICA
      "sugerencias": [
        "El tiempo promedio de 8h es excesivo. Considere dividir esta tarea.",
        "La tasa de rechazo del 35% indica criterios poco claros en el formulario."
      ]
    }
  ]
```

---

### 9.3 — Backend: calcular métricas antes de llamar a la IA

**Archivo:** `wm_backend/.../ia/service/AnalisisPoliticaService.java`

```
Método: calcularMetricasPorNodo(String politicaId) -> List<MetricasNodoDto>

Proceso:
  1. Obtener todos los nodos de la política
  2. Para cada nodo:
     a. Buscar todas las EjecucionNodo con ese nodo_id (incluyendo históricas)
     b. Calcular tiempo_promedio_minutos:
        → Para ejecuciones COMPLETADO: (completado_en - iniciado_en) en minutos
        → Promedio de todas las ejecuciones completadas
     c. cantidad_ejecuciones: count de ejecuciones con estado PENDIENTE o EN_PROCESO
     d. tasa_rechazo: count(RECHAZADO) / count(total) para ese nodo
     e. tiempo_espera_promedio: (iniciado_en - creadoEn) en minutos, promedio
     f. varianza_tiempo: varianza estadística de los tiempos de completado
  3. Retornar lista de MetricasNodoDto

Si un nodo tiene 0 ejecuciones históricas → excluirlo del análisis
(no hay datos suficientes para predecir)
```

**Endpoint:** `POST /ia/analizar-politica`
```
Body: { politicaId: "..." }
Proceso:
  1. Llamar a AnalisisPoliticaService.calcularMetricasPorNodo(politicaId)
  2. Si no hay suficientes datos → retornar 200 con mensaje "Datos insuficientes para análisis.
     Se necesitan al menos 5 trámites completados."
  3. Hacer POST al microservicio Python con las métricas calculadas
  4. Guardar el resultado en colección analisis_ia
  5. Retornar el resultado al frontend
```

---

### 9.4 — Frontend: mostrar análisis de IA

**Archivo:** `wm_frontend/.../admin/pages/politicas/lista/politica-detalle.component.ts`

```
Botón "🔍 Analizar con IA":
  1. Mostrar spinner "Analizando política..."
  2. POST /ia/analizar-politica con { politicaId }
  3. Al recibir respuesta:
     a. Mostrar cards de resultados por nodo:
        - Cada card: nombre del nodo, severidad (badge color), probabilidad %, sugerencias
        - Si es_cuello_botella=true → card con borde rojo
     b. Resaltar el nodo crítico en el diagrama:
        - Abrir o actualizar el editor (si está visible) con el nodo del cuello de botella
          marcado con borde rojo pulsante (CSS animation: @keyframes pulse)
        - O mostrar un badge "⚠️ Cuello de botella" sobre el nodo en el monitor
  4. Guardar el análisis en localStorage para no re-analizar si el usuario navega
```

¿Qué cambió?
analisis_service.py — Reescrito con:

Mejora	Antes	Ahora
Enriquecimiento	Valores faltantes = defaults	_enriquecer_metricas() calcula las 10 features con patrones realistas según rol del nodo
Bottleneck por rol	Todos los nodos iguales	Verific/Inspección → ⬆️ tiempo+backlog, Legal/Contrato → ⬆️ espera, Escalamiento → siempre cuello
Proporción entrenamiento	75% normal / 25% cuello	65% normal / 35% cuello (más sensible)
Umbral detección	>0.5 = cuello	>0.4 = cuello (detecta más)
Severidad	>0.75 crítica	>0.70 crítica (calibrado más agresivo)
Sugerencias	4 genéricas	15+ específicas por contexto (firma electrónica, checklist digital, SLAs, etc.)
Consistencia	Random cada vez	Seed determinístico por nodo_id (mismos resultados cada vez)
Versionado	Sin versión	v2_realistic → fuerza reentrenamiento automático

---

### 9.5 — Formulario generado por IA en el front (Admin Depto)

**Archivo:** `wm_frontend/.../admin-depto/pages/formularios/formulario-editor.component.ts`

```
Botón "✨ Generar campos con IA":
  → Abrir modal con textarea: "Describe qué datos necesitas capturar en este paso..."
  → Al confirmar: POST /ia/generar-formulario con { descripcion: "..." }
  → Al recibir campos generados:
     - Añadir los campos al formulario actual (no reemplazar, AGREGAR)
     - Cada campo aparece en el editor como editable
     - El usuario puede modificar nombre, tipo, eliminar campos antes de guardar
  → Toast: "Se generaron X campos. Revísalos antes de guardar."
```

---

## DÍA 10 — Pulido UI/UX + Validaciones

### 10.1 — Frontend: errores y estados de carga

```
Regla global: CADA llamada HTTP debe tener:
  - Estado de carga (spinner o skeleton)
  - Manejo de error (mensaje amigable, no "Http failure response")
  - Estado vacío (mensaje cuando la lista está vacía)

Interceptor de errores (ya debe existir, verificar que maneja):
  - 400 → mostrar response.error.message
  - 401 → limpiar JWT → navegar a /login con toast "Sesión expirada"
  - 403 → toast "No tienes permisos para esta acción"
  - 404 → toast "Recurso no encontrado"
  - 422 → mostrar errores de validación campo por campo
  - 503 → toast "Servicio de IA no disponible temporalmente"
  - 500 → toast "Error interno del servidor. Intenta de nuevo."
```

### 10.2 — Frontend: UX del editor de diagrama

```
Indicador de cambios sin guardar:
  - Si modificado=true → mostrar punto rojo en el botón "💾 Guardar"
  - Al intentar navegar fuera con cambios → confirmar con dialog

Zoom y pan del canvas:
  - Scroll del mouse → zoom in/out
  - Click + drag en área vacía → mover el canvas (pan)
  - Botón "⊞ Ajustar" → graph.fit() para centrar todo el diagrama

Undo/Redo:
  - @jointjs mxUndoManager
  - Ctrl+Z → deshacer último cambio
  - Ctrl+Y o Ctrl+Shift+Z → rehacer
  - Botones en la barra de herramientas'
 Quiero que aparte el boton actual de agrandar me muestre igual el panel de los elementos del diagrama para que pueda arrastralo al lienzo
 Quiero que se disminuya el tamano de las nodos y de los carriles, flechas y de todo en general un 20%, aparte que mejor si lo ahces todo mas delgado, me refiero a que de ancho disminuyas aun mas, un 35%, hay varios nodos o rombos que tienen demasiado espacio en blanco, al igual que los carriles o las flechas son demasiado largas, asi que mejor disminuye mas de ancho que de largo para que quede mejor y se vean el diagrama casi completo en toda la pantalla

 En el diagrama se podria poner a los titulo de departamentos estaticos, o nose que nombre es que que se quedan en esa posicion mientras yo sigo bajando mas el diagrama de esa manera si se a que departamento le pertence por mas que este bien abajo del diagrama

```

### 10.3 — Backend: validaciones adicionales

```
En PoliticaService.activar(id):
  Antes de cambiar estado a ACTIVA, validar:
  1. La política tiene al menos 1 nodo INICIO y 1 nodo FIN
  2. Todos los carriles usados corresponden a departamentos con admin asignado
  3. Existe al menos 1 transición (el diagrama no puede estar vacío)
  4. No hay nodos huérfanos (sin transiciones de entrada ni salida, excepto INICIO y FIN)
  Si alguna validación falla → BadRequestException con mensaje descriptivo

En EjecucionService:
  Al completar una ejecución, verificar que el funcionario pertenece al departamento del nodo.
  Si no → UnauthorizedException("No perteneces al departamento de esta tarea")
```

### 10.4 — Flutter: mejoras de UX

```
Splash screen:
  - Logo de WorkflowManager centrado sobre fondo color --bg-dark
  - Animación de fade in (1 segundo)
  - Verificar si hay token guardado → si hay → ir a pantalla principal,
    si no → ir a LoginScreen

Manejo de conectividad:
  - Mostrar banner "Sin conexión" cuando no hay internet
  - Usar connectivity_plus package para detectar el estado de red

Estados de carga mejorados:
  - Usar Shimmer effect en lugar de CircularProgressIndicator para listas
  - Shimmer imita la forma de los cards que van a aparecer
```

---

## DÍA 11 — Testing End-to-End completo

### Flujo de prueba completo (ejecutar en orden, sin saltarse pasos)

```
BLOQUE 1 — Estructura organizacional:
  □ Login con admin@cre.bo → accede como Admin General
  □ Ver lista de departamentos → 5 departamentos del seeder con sus admins
  □ Crear nuevo departamento "Soporte" → aparece sin admin (badge naranja "Sin admin")
  □ Crear usuario funcionario → asignar a departamento "Soporte"
  □ Crear usuario admin → asignar a "Soporte" → departamento.admin_departamento_id se actualiza
  □ Intentar crear otro admin para "Soporte" → error "Ya tiene admin asignado"
  □ Eliminar el departamento "Soporte" → falla si tiene usuarios
  □ Eliminar usuarios primero → eliminar departamento → OK

BLOQUE 2 — Editor de diagrama:
  □ Abrir política "Baja de Servicio" del seeder → diagrama carga correctamente
  □ Agregar un nuevo nodo TAREA al carril "Facturación" → se asigna automáticamente
  □ Conectar el nuevo nodo con flechas → arrows funcionan
  □ Intentar agregar carril de "Soporte" (sin admin) → no aparece en la lista
  □ Intentar guardar sin nodo FIN → error de validación
  □ Guardar con todo correcto → toast de éxito → cerrar y reabrir → diagrama igual
  □ Exportar PNG → imagen correcta
  □ Exportar PDF → PDF correcto

BLOQUE 3 — Trámite completo:
  □ Activar política "Instalación de Nuevo Medidor"
  □ Iniciar trámite "Instalación medidor - Juan Pérez" con prioridad ALTA
  □ Login como func1.atencion@cre.bo → ver tarea en lista → prioridad ALTA en rojo
  □ Completar tarea → formulario se guarda → desaparece de la lista
  □ Monitor muestra nodo "Técnico" en amarillo (automático, sin recargar)
  □ Login como func1.tecnico@cre.bo → ver tarea de verificación técnica
  □ Completar con "Aprobado" → motor evalúa DECISION → va a Facturación
  □ Monitor muestra Facturación en amarillo
  □ func1.facturacion completa → trámite COMPLETADO
  □ Monitor muestra todos en verde

BLOQUE 4 — Flutter:
  □ Login en APK como func1.atencion@cre.bo → ver tareas pendientes
  □ Al completar tarea en web → Flutter recibe push notification
  □ Tap en la notificación → navega a la pantalla de tareas
  □ Completar tarea desde Flutter → monitor web actualiza

BLOQUE 5 — IA:
  □ Generar diagrama por texto → describe proceso → diagrama aparece en canvas
  □ Generar diagrama por voz → grabar audio → diagrama correcto
  □ Editar el diagrama generado → guardar → funciona igual que manual
  □ Admin Depto genera formulario con IA → campos aparecen editables → guardar
  □ Analizar política con IA → resultado con sugerencias → nodo cuello resaltado

BLOQUE 6 — Seeder:
  □ Los 4 trámites de prueba del seeder están visibles en el monitor
  □ La política de "Reclamo" muestra el ciclo correctamente
  □ La política de "Reconexión" muestra el fork/join correctamente
```

---

## DÍA 12 — Deploy Azure — Backend + IA (CU-15)

### 12.1 — Preparación

```
Antes de desplegar, verificar:
  □ mvn clean package -DskipTests → BUILD SUCCESS
  □ APK Flutter compila: flutter build apk --release → sin errores
  □ docker build -t workflow-ia . → sin errores (en la carpeta workflow-ia)
  □ Todas las variables de entorno documentadas en README

Servicios Azure a crear en orden:
  1. Resource Group: rg-workflow-parcial (East US o Brazil South)
  2. MongoDB Atlas M0 (gratis) → obtener connection string
  3. Azure Container Registry: acrworkflow{tuusername}
  4. Azure App Service: Plan B1 (el más barato con siempre encendido)
  5. Azure Container Apps (para el microservicio IA)
  6. Azure Blob Storage: cuenta wmstorage{tuusername}
  7. Azure Static Web Apps (para Angular)
```

### 12.2 — Deploy del microservicio IA

```
Orden importante: desplegar IA ANTES que el backend principal,
porque el backend necesita la URL de la IA como variable de entorno.

Pasos:
  1. Construir imagen Docker:
     docker build -t workflow-ia:v1 ./workflow-ia

  2. Subir al Container Registry:
     az acr login --name acrworkflow{username}
     docker tag workflow-ia:v1 acrworkflow{username}.azurecr.io/workflow-ia:v1
     docker push acrworkflow{username}.azurecr.io/workflow-ia:v1

  3. Crear Container App:
     - Imagen: acrworkflow{username}.azurecr.io/workflow-ia:v1
     - Puerto: 8001
     - Variables de entorno: (ninguna obligatoria si no usas OpenAI)
     - Ingress: externo, puerto 8001
     - CPU: 0.5, Memoria: 1Gi (suficiente para spaCy)

  4. Al crear el Container App, el modelo se entrenará la primera vez que inicie
     (train_model.py se ejecuta automáticamente si no existe el .pkl)
     PROBLEMA: el .pkl no persiste si el container se reinicia
     SOLUCIÓN: agregar en el Dockerfile:
       RUN python train_model.py
       (entrenar durante el build de la imagen, no en runtime)

  5. Obtener la URL del Container App → guardar para el paso siguiente
```

### 12.3 — Deploy del backend Spring Boot

```
Variables de entorno a configurar en Azure App Service > Configuration:
  MONGODB_URI              → mongodb+srv://user:pass@cluster.mongodb.net/workflow_db
  JWT_SECRET               → generado aleatoriamente (mínimo 64 caracteres)
  JWT_EXPIRATION           → 86400000
  IA_SERVICE_URL           → URL del Container App del paso anterior
  FIREBASE_CREDENTIALS     → base64 del google-services JSON del servidor
  AZURE_STORAGE_CONNECTION_STRING → del Storage Account creado
  SPRING_PROFILES_ACTIVE   → prod

Pasos de deploy:
  1. mvn clean package -DskipTests
  2. az webapp deploy --resource-group rg-workflow-parcial
     --name wm-backend --src-path target/workflow-back-0.0.1-SNAPSHOT.jar --type jar

  3. Verificar logs: az webapp log tail --name wm-backend --resource-group rg-workflow-parcial
  4. Probar: curl https://wm-backend.azurewebsites.net/actuator/health → {"status":"UP"}

IMPORTANTE — MongoDB Atlas whitelist:
  - Agregar la IP del App Service de Azure a la whitelist de MongoDB Atlas
  - O temporalmente usar 0.0.0.0/0 (no recomendado para producción real)
  - Mejor opción: usar "Allow access from Azure" en Atlas si está disponible
```

---

## DÍA 13 — Deploy Frontend + APK Flutter

### 13.1 — Deploy Angular en Azure Static Web Apps

```
Antes del build:
  - Actualizar environment.prod.ts con las URLs reales de Azure
  - Configurar CORS en SecurityConfig.java para aceptar el dominio de Azure Static Web Apps
    (agregarla como origen permitido → hacer redeploy del backend)

Build:
  ng build --configuration production

Deploy:
  Opción A (automático con GitHub):
    - Crear Azure Static Web App y conectar el repo wm_frontend
    - Azure genera un archivo .github/workflows/azure-static-web-apps-xxx.yml
    - Cada push a main → deploy automático

  Opción B (manual):
    az staticwebapp deploy
      --name wm-frontend
      --resource-group rg-workflow-parcial
      --source ./dist/workflow-front

Configurar redirects para SPA:
  Crear archivo: wm_frontend/public/staticwebapp.config.json
  Contenido:
    {
      "navigationFallback": {
        "rewrite": "/index.html",
        "exclude": ["/api/*", "/*.{css,js,png,jpg}"]
      }
    }
  SIN este archivo, al refrescar una ruta como /admin/politicas → 404 del servidor.

Probar:
  - Abrir la URL de Azure Static Web Apps
  - Login funciona
  - Navegar a /admin/departamentos → no hay 404
  - Abrir editor de diagrama → carga correctamente
  - WebSocket conecta (verificar que no hay bloqueo por CORS o protocolo)
```

### 13.2 — APK Flutter para producción

```
Actualizar antes del build:
  wm_mobile/lib/core/constants/api_url.dart:
    baseUrl = 'https://wm-backend.azurewebsites.net'
    wsUrl   = 'wss://wm-backend.azurewebsites.net/ws'
    (importante: ws → wss para conexiones seguras en producción)

Build:
  flutter build apk --release

Verificar el APK:
  - Instalar en dispositivo físico (preferible) o emulador
  - Login funciona con las credenciales del seeder
  - Las notificaciones push llegan (requiere dispositivo físico con Google Services)
  - El WebSocket del monitor conecta correctamente

Distribuir el APK:
  Opción recomendada para demostración:
  - Subir a Firebase App Distribution (gratuito) → comparte el link con Martinez
  - O subir el .apk a un Google Drive y poner el link en el README
```

---

## DÍA 14 — Documentación técnica

### 14.1 — Swagger

```
El Swagger ya debe estar configurado desde el inicio (springdoc-openapi).
Verificar en producción: https://wm-backend.azurewebsites.net/swagger-ui.html

Para cada Controller, agregar si falta:
  @Tag(name = "NombreModulo", description = "Descripción")
  @Operation(summary = "Breve descripción del endpoint")
  @ApiResponse(responseCode = "200", description = "Éxito")
  @ApiResponse(responseCode = "401", description = "No autenticado")
  @ApiResponse(responseCode = "403", description = "Sin permisos")
```

### 14.2 — Colección Postman

```
Exportar una colección Postman con:
  - Carpeta "Auth": POST /auth/registro, POST /auth/login
  - Carpeta "Empresa": CRUD completo
  - Carpeta "Departamentos": CRUD + endpoints especiales
  - Carpeta "Usuarios": CRUD
  - Carpeta "Políticas": CRUD + activar + desactivar + diagrama
  - Carpeta "Nodos": CRUD
  - Carpeta "Transiciones": CRUD
  - Carpeta "Formularios": CRUD
  - Carpeta "Trámites": CRUD + iniciar
  - Carpeta "Ejecuciones": completar + rechazar + iniciar
  - Carpeta "IA": generar-diagrama + analizar-politica + generar-formulario

Variables de entorno en Postman:
  baseUrl = https://wm-backend.azurewebsites.net
  token   = (se llena automáticamente con un test en el login)

Test automático en POST /auth/login:
  pm.environment.set("token", pm.response.json().data.token);
```

---

## DÍAS 15–18 — Documentación PUDS para entrega

### Estructura del documento (recordatorio detallado)

```
PORTADA:
  - Logo UAGRM
  - Título: "Sistema de Gestión de Trámites y Políticas de Negocio"
  - Materia: Ingeniería de Software I
  - Docente: Ing. Rolando Antonio Martínez Canedo
  - Estudiantes: nombre + carnet de cada uno
  - Grupo: X
  - Fecha: fecha de entrega

TABLA DE CONTENIDOS: automática

CAPÍTULO 1 — FUNDAMENTACIÓN TEÓRICA:

  1.1 Proceso Unificado de Desarrollo de Software (PUDS)
      - Fases: Inicio, Elaboración, Construcción, Transición
      - Flujos de trabajo: Requisitos, Análisis, Diseño, Implementación, Pruebas
      - Diagrama de fases vs flujos (imagen del libro de Jacobson)

  1.2 BPM / Workflow / Políticas de Negocio
      - Definición de proceso de negocio
      - Motor de workflow: concepto y funcionamiento
      - Diferencia entre BPMN y UML Activity Diagrams
      - Por qué UML 2.5 y no BPMN para este sistema

  1.3 UML 2.5 — Diagrama de Actividades
      - Notación oficial (Initial Node, Action, Decision, Fork/Join, Final)
      - Swimlanes y su uso para representar departamentos
      - Guard conditions en transiciones
      - Diferencias entre UML 2.0 y 2.5

  1.4 WebSockets vs Polling
      - Problema del polling: carga innecesaria al servidor
      - WebSocket: protocolo full-duplex
      - STOMP sobre WebSocket: protocolo de mensajería
      - Por qué no usar polling para el monitor

  1.5 Inteligencia Artificial en Ingeniería de Software
      - Diferencia entre usar una API (ej: OpenAI) vs usar un modelo propio
      - spaCy: modelo NLP pre-entrenado en español (es_core_news_md)
        - Pipeline de NLP: tokenización, POS tagging, NER, dependency parsing
        - Cómo se usó para extraer departamentos y acciones del texto
      - scikit-learn: IsolationForest y RandomForest
        - Por qué datos sintéticos son válidos para un sistema nuevo
        - Métricas de evaluación del modelo (accuracy, precision, recall, F1)
      - Reconocimiento de voz: SpeechRecognition + Google Speech API

  1.6 Spring Boot + MongoDB
      - Por qué MongoDB para este sistema (documentos flexibles, JSON nativo)
      - Spring Data MongoDB: @Document, MongoRepository
      - Comparación con ORM relacional (JPA/Hibernate)
      - Por qué no usar una BD relacional para este proyecto

  1.7 Microsoft Azure
      - Azure App Service: hosting de aplicaciones Java
      - Azure Container Apps: para microservicios containerizados
      - Azure Static Web Apps: hosting de SPAs
      - Azure Blob Storage: almacenamiento de archivos
      - MongoDB Atlas: base de datos en la nube compatible con Azure

CAPÍTULO 2 — PUDS CICLO 1 (CU-01 al CU-05):
  2.1 Captura de Requisitos
      - Tabla de actores (A1, A2, A3, A4)
      - Tabla de casos de uso (ID, nombre, actores, descripción, prioridad)
      - Especificación detallada de cada CU (tabla: propósito, actores, flujo, pre/post, excepciones)
      - Diagrama de casos de uso (imagen)

  2.2 Análisis
      - Identificar paquetes (auth, empresa, usuario, departamento, formulario)
      - Relacionar paquetes y CU (diagrama)
      - Diagramas de colaboración/comunicación por cada CU

  2.3 Diseño
      - Arquitectura física (diagrama de despliegue)
      - Arquitectura lógica (capas: Controller, Service, Repository, Model)
      - Diagrama de clases (de los módulos del Ciclo 1)
      - Diagramas de secuencia (CU-01 login, CU-02 crear departamento, etc.)

  2.4 Implementación
      - Justificación de Spring Boot vs otras opciones
      - Justificación de MongoDB vs PostgreSQL
      - Justificación de Angular 17 vs React/Vue
      - Capturas de pantalla del sistema funcionando (login, departamentos, usuarios)

CAPÍTULO 3 — PUDS CICLO 2 (CU-06 al CU-11):
  (misma estructura que Ciclo 1)
  Agregar en implementación:
  - Captura del editor de diagrama con UML 2.5
  - Captura del monitor en tiempo real
  - Captura de Flutter con tareas y notificaciones

CAPÍTULO 4 — PUDS CICLO 3 (CU-12 al CU-15):
  (misma estructura)
  Agregar en implementación:
  - Salida del script train_model.py (métricas del modelo)
  - Ejemplo de diagrama generado por IA desde texto
  - Ejemplo de diagrama generado por voz
  - Captura del análisis de cuellos de botella
  - Evidencia del deploy en Azure (URLs públicas)

CAPÍTULO 5 — DOCUMENTACIÓN DE USO DEL SOFTWARE:
  5.1 Manual del Admin General (capturas de cada pantalla con descripción)
  5.2 Manual del Admin Departamento
  5.3 Manual del Funcionario (web y mobile)
  5.4 Cómo usar la IA (texto y voz)

BIBLIOGRAFÍA (formato APA):
  - Jacobson, I., Booch, G., Rumbaugh, J. (1999). The Unified Software Development Process.
  - Object Management Group. (2017). UML 2.5.1 Specification.
  - Scikit-learn documentation: sklearn.ensemble.IsolationForest y RandomForestClassifier
  - spaCy documentation: es_core_news_md model
  - Spring Boot 3.2 Reference Documentation
  - MongoDB Manual 7.0
  - Microsoft Azure Documentation

ANEXOS:
  - QR código repo wm_backend (GitHub)
  - QR código repo wm_frontend (GitHub)
  - QR código repo wm_mobile (GitHub)
  - QR descarga APK
  - Links de producción Azure:
    Backend: https://wm-backend.azurewebsites.net/swagger-ui.html
    Frontend: https://wm-frontend.azurestaticapps.net
  - Credenciales de prueba para demostración:
    Admin General: admin@cre.bo / Admin123!
    Admin Depto Técnico: admin.tecnico@cre.bo / Admin123!
    Funcionario Atención: func1.atencion@cre.bo / Func123!
```

---

## CHECKLIST FINAL — Lo que debe funcionar el día de la entrega

```
AUTENTICACIÓN Y ROLES:
  □ Login diferenciado para los 3 roles (web y mobile)
  □ Guards funcionando: cada rol solo accede a su módulo
  □ JWT expira correctamente y redirige a login

GESTIÓN ORGANIZACIONAL:
  □ Departamentos se crean sin admin
  □ Al crear Admin Depto → departamento se actualiza automáticamente
  □ No se puede crear segundo admin para el mismo departamento
  □ Lista de departamentos muestra badge "Sin admin" cuando corresponde

EDITOR DE DIAGRAMA:
  □ Carriles son VERTICALES (columnas) — NO horizontales
  □ Formas UML 2.5 correctas sin fondo blanco (transparentes)
  □ Las flechas se pueden crear arrastrando desde los nodos
  □ Los nodos se asignan al carril por posición visual
  □ Validaciones bloquean guardar si el diagrama es inválido
  □ Solo aparecen departamentos con admin asignado en la lista de carriles
  □ Guardar → cerrar → reabrir → diagrama idéntico
  □ Exportar PNG y PDF funcionan

MOTOR DE WORKFLOW:
  □ Trámite avanza automáticamente al siguiente nodo al completar
  □ DECISION evalúa condición y va al nodo correcto
  □ PARALELO crea múltiples ejecuciones simultáneas
  □ JOIN espera a que todos los paralelos estén completos antes de avanzar
  □ Ciclos funcionan sin errores

TIEMPO REAL:
  □ Monitor muestra verde/amarillo/rojo según estado
  □ Los colores se actualizan SIN recargar la página (WebSocket)
  □ El monitor de Flutter también se actualiza en tiempo real

NOTIFICACIONES:
  □ Funcionario recibe push al asignársele una tarea
  □ Admin recibe push al completarse un trámite
  □ Campana web muestra notificaciones no leídas

IA — MODELOS DEMOSTRABLES:
  □ Ejecutar train_model.py en vivo → muestra métricas → accuracy > 85%
  □ POST /ia/generar-diagrama con texto → retorna JSON de nodos UML válido
  □ POST /ia/generar-diagrama con audio → transcribe y retorna diagrama
  □ POST /ia/analizar-politica → detecta cuello de botella → sugerencias
  □ El diagrama generado por IA es editable en el canvas
  □ spaCy cargado y demostrable (mostrar tokens y entidades en consola)

AZURE:
  □ Backend accesible: https://wm-backend.azurewebsites.net/swagger-ui.html
  □ Frontend accesible: https://wm-frontend.azurestaticapps.net
  □ IA accesible: URL del Container App
  □ APK instalable y funcional en dispositivo Android real

DOCUMENTACIÓN:
  □ Swagger completo en producción
  □ Colección Postman con todos los endpoints
  □ README en los 3 repos
  □ Documento PUDS 3 ciclos completo
  □ APK link en README o QR en anexo del documento
```

---

*Parcial 1 — Ingeniería de Software I — Ing. Martínez Canedo*
*Stack: Spring Boot · Angular · Flutter · FastAPI · spaCy · scikit-learn · MongoDB · Azure*
