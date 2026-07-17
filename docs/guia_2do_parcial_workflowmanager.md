# Guía de Implementación — 2do Parcial WorkflowManager
**Materia:** Ingeniería de Software I — Ing. Martínez Canedo  
**Ciclo 2 — PUDS** | Fecha de entrega: **Viernes 12 de Junio**  
**Stack:** Spring Boot · Angular (PWA) · Flutter (Offline) · Python FastAPI · MongoDB · AWS S3

---

## ⚠️ ADVERTENCIAS CRÍTICAS ANTES DE EMPEZAR

### WARNING 1 — Diagrama Colaborativo (verificar si ya está)
El ingeniero en el 1er parcial pedía que **dos administradores entren al editor de diagrama simultáneamente y que cuando uno arrastre/elimine un nodo, el otro lo vea en tiempo real**. Esto se perdió puntos en el 1er parcial.

**Antes de avanzar, el agente DEBE verificar:**
- `wm_backend/src/.../config/WebSocketConfig.java` — ¿tiene topic `/politicas/{id}/diagrama`?
- `wm_frontend/src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.ts` — ¿tiene suscripción WebSocket que actualiza el canvas cuando llega un evento externo?

Si NO está implementado, el Agente 0 lo implementa antes de todo lo demás.

### WARNING 2 — Migración Django → FastAPI
El `wm_ai` actual usa Django. **Todo debe migrarse a FastAPI**. El Agente 1 hace esto primero, preservando toda la lógica existente de spaCy + Groq + el modelo ML.

### WARNING 3 — Modelo ML → Deep Learning
El modelo actual (`RandomForest + GradientBoosting + IsolationForest`) debe ser **reemplazado** por una red neuronal con TensorFlow/Keras. Los datos sintéticos de entrenamiento se generan igual, pero el modelo cambia.

---

## División de Agentes

| Agente | Responsabilidad | Repos tocados |
|--------|----------------|---------------|
| **Agente 0** | Diagrama colaborativo (WebSocket en tiempo real) | wm_backend, wm_frontend |
| **Agente 1** | Migración Django → FastAPI + preservar IA existente | wm_ai |
| **Agente 2** | Deep Learning: reemplazar modelo ML (TensorFlow) | wm_ai |
| **Agente 3** | NLP Deep Learning: reportes dinámicos por voz/texto | wm_ai, wm_backend |
| **Agente 4** | AWS S3 + modelo de datos documental (backend Java) | wm_backend |
| **Agente 5** | API REST completa de gestión documental | wm_backend |
| **Agente 6** | OnlyOffice colaborativo + permisos | wm_backend, docker |
| **Agente 7** | Frontend Angular: módulo documental + PWA | wm_frontend |
| **Agente 8** | Frontend Angular: reportes dinámicos + dashboard IA | wm_frontend |
| **Agente 9** | Flutter: offline-first + documentos + mejoras | wm_mobile |
| **Agente 10** *(viernes)* | Agente/Chatbot cliente | todos |
| **Agente 11** *(opcional)* | DynamoDB | wm_backend |

---

---

# AGENTE 0 — Diagrama Colaborativo en Tiempo Real

## Contexto
El editor de diagramas ya existe en `editor-diagrama.component.ts` con JointJS. El backend ya tiene WebSockets (Spring STOMP). Lo que falta es sincronizar los eventos del canvas entre múltiples usuarios conectados al mismo diagrama.

## Paso 1 — Verificar el estado actual

Revisar si existe en `WebSocketConfig.java`:
```java
registry.enableSimpleBroker("/topic", "/user");
```

Revisar si en `editor-diagrama.component.ts` existe algo como:
```typescript
this.stompClient.subscribe(`/topic/diagrama/${this.politicaId}`, ...)
```

Si no existe ninguno de los dos → implementar desde aquí.

## Paso 2 — Backend: nuevo topic de diagrama

**Archivo:** `wm_backend/src/main/java/com/workflow/diagrama/controller/DiagramaWebSocketController.java` *(crear)*

```java
@Controller
public class DiagramaWebSocketController {

    @MessageMapping("/diagrama/{politicaId}/evento")
    @SendTo("/topic/diagrama/{politicaId}")
    public DiagramaEventoDTO procesarEvento(
            @DestinationVariable String politicaId,
            DiagramaEventoDTO evento) {
        // Solo retransmite el evento a todos los suscriptores
        return evento;
    }
}
```

**Archivo:** `wm_backend/src/main/java/com/workflow/diagrama/dto/DiagramaEventoDTO.java` *(crear)*

```java
@Data
public class DiagramaEventoDTO {
    private String tipo;        // MOVER_NODO, ELIMINAR_NODO, AGREGAR_NODO, AGREGAR_TRANSICION, ELIMINAR_TRANSICION
    private String elementoId;  // ID del nodo o transición afectado
    private String usuarioId;   // quién hizo el cambio (para no reaplicarlo en su propia pantalla)
    private Map<String, Object> datos; // posición nueva, nombre nuevo, etc.
}
```

## Paso 3 — Frontend Angular: sincronizar JointJS por WebSocket

**Archivo:** `editor-diagrama.component.ts` *(modificar)*

```typescript
// Al inicializar el componente, suscribirse al topic
private suscribirseAlDiagrama(): void {
  this.stompClient.subscribe(
    `/topic/diagrama/${this.politicaId}`,
    (mensaje) => {
      const evento: DiagramaEvento = JSON.parse(mensaje.body);
      // No reaplicar el evento propio
      if (evento.usuarioId === this.usuarioActualId) return;
      this.aplicarEventoExterno(evento);
    }
  );
}

// Cuando el usuario local mueve un nodo
private onNodoMovido(nodoId: string, x: number, y: number): void {
  // 1. Actualizar visualmente (ya lo hace JointJS)
  // 2. Emitir al WebSocket
  this.stompClient.publish({
    destination: `/app/diagrama/${this.politicaId}/evento`,
    body: JSON.stringify({
      tipo: 'MOVER_NODO',
      elementoId: nodoId,
      usuarioId: this.usuarioActualId,
      datos: { x, y }
    })
  });
}

// Aplicar evento que llegó de otro usuario
private aplicarEventoExterno(evento: DiagramaEvento): void {
  switch (evento.tipo) {
    case 'MOVER_NODO':
      const cell = this.graph.getCell(evento.elementoId);
      if (cell) cell.position(evento.datos['x'], evento.datos['y']);
      break;
    case 'ELIMINAR_NODO':
      const cell = this.graph.getCell(evento.elementoId);
      if (cell) cell.remove();
      break;
    case 'AGREGAR_NODO':
      // reconstruir el nodo desde evento.datos
      this.agregarNodoDesdeEvento(evento.datos);
      break;
    case 'AGREGAR_TRANSICION':
      this.agregarTransicionDesdeEvento(evento.datos);
      break;
    case 'ELIMINAR_TRANSICION':
      const link = this.graph.getCell(evento.elementoId);
      if (link) link.remove();
      break;
  }
}
```

**Mostrar quién está editando (presencia):**
```typescript
// Al entrar al editor, anunciar presencia
private anunciarPresencia(): void {
  this.stompClient.publish({
    destination: `/app/diagrama/${this.politicaId}/evento`,
    body: JSON.stringify({
      tipo: 'USUARIO_CONECTADO',
      usuarioId: this.usuarioActualId,
      datos: { nombre: this.usuarioActualNombre, color: this.colorAsignado }
    })
  });
}
// Mostrar avatares/colores de usuarios activos en la toolbar del editor
```

## Paso 4 — Cómo probar

1. Abrir dos navegadores (uno normal, uno en incógnito)
2. Loguearse con dos cuentas de ADMIN_GENERAL distintas
3. Abrir la misma política en el editor en ambos
4. En el browser 1: mover un nodo → debe verse en el browser 2 en tiempo real
5. En el browser 1: eliminar un nodo → debe desaparecer en el browser 2
6. En el browser 1: agregar un nodo → debe aparecer en el browser 2

---

---

# AGENTE 1 — Migración Django → FastAPI

## Contexto
El `wm_ai` actual tiene Django. Hay que migrar a FastAPI preservando exactamente la misma lógica de:
- `diagrama_service.py` (spaCy + Groq)
- `formulario_service.py` (Groq)
- `analisis_service.py` (modelo ML — temporalmente, hasta que el Agente 2 lo reemplace)

## Paso 1 — Nueva estructura del proyecto

```
wm_ai/
├── main.py                          ← entrada FastAPI
├── requirements.txt                 ← actualizado
├── Dockerfile                       ← actualizado
├── .env
├── train_model.py                   ← se actualizará en Agente 2
├── modelo_cuello_botella.h5         ← nuevo formato TensorFlow (Agente 2)
│
└── app/
    ├── core/
    │   ├── config.py                ← variables de entorno con pydantic-settings
    │   └── groq_client.py           ← singleton del cliente Groq
    │
    ├── models/
    │   └── schemas.py               ← todos los Pydantic models
    │
    ├── routers/
    │   ├── diagrama.py              ← POST /ia/generar-diagrama
    │   ├── formulario.py            ← POST /ia/generar-formulario
    │   ├── analisis.py              ← POST /ia/generar-analisis
    │   ├── enrutamiento.py          ← POST /ia/enrutar-solicitud
    │   └── reportes.py              ← POST /ia/generar-reporte
    │
    └── services/
        ├── diagrama_service.py
        ├── formulario_service.py
        ├── analisis_service.py
        └── reporte_service.py       ← Agente 3
```

## Paso 2 — requirements.txt actualizado

```txt
fastapi==0.110.0
uvicorn[standard]==0.29.0
pydantic==2.6.4
pydantic-settings==2.2.1
python-dotenv==1.0.1
groq==0.5.0
spacy==3.7.4
# Modelo español: python -m spacy download es_core_news_sm
numpy==1.26.4
pandas==2.2.1
scikit-learn==1.4.2        # se mantiene temporalmente hasta Agente 2
tensorflow==2.16.1         # nuevo — para Agente 2 y 3
transformers==4.40.1       # nuevo — para NLP reportes (Agente 3)
torch==2.3.0               # requerido por transformers
sentencepiece==0.2.0       # tokenización
python-multipart==0.0.9    # para recibir archivos si hace falta
httpx==0.27.0
```

## Paso 3 — main.py

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import diagrama, formulario, analisis, enrutamiento, reportes
from app.core.config import settings
import spacy

app = FastAPI(
    title="WorkflowManager AI Service",
    description="Microservicio de inteligencia artificial para WorkflowManager",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(diagrama.router, prefix="/ia", tags=["Diagrama"])
app.include_router(formulario.router, prefix="/ia", tags=["Formulario"])
app.include_router(analisis.router, prefix="/ia", tags=["Analisis"])
app.include_router(enrutamiento.router, prefix="/ia", tags=["Enrutamiento"])
app.include_router(reportes.router, prefix="/ia", tags=["Reportes"])

@app.get("/health")
def health():
    return {"status": "ok", "service": "WorkflowManager IA v2", "framework": "FastAPI"}

@app.on_event("startup")
async def startup_event():
    # Precargar modelo spaCy al iniciar
    try:
        spacy.load("es_core_news_sm")
        print("✅ spaCy cargado")
    except Exception as e:
        print(f"⚠️ spaCy no disponible: {e}")
```

## Paso 4 — app/core/config.py

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    groq_api_key: str = ""
    aws_access_key_id: str = ""
    aws_secret_access_key: str = ""
    aws_region: str = "us-east-1"
    aws_s3_bucket: str = "wm-documentos"
    model_version: str = "v4_tensorflow"

    class Config:
        env_file = ".env"

settings = Settings()
```

## Paso 5 — Migrar cada service de Django a FastAPI

Los servicios `diagrama_service.py`, `formulario_service.py` y `analisis_service.py` son **Python puro** — no tienen dependencia de Django. Solo hay que copiarlos y ajustar los imports si usaban `django.conf.settings` para leer variables de entorno, reemplazando con `from app.core.config import settings`.

**Revisar en cada service:**
```python
# ❌ Antes (Django)
from django.conf import settings
api_key = settings.GROQ_API_KEY

# ✅ Después (FastAPI)
from app.core.config import settings
api_key = settings.groq_api_key
```

## Paso 6 — Cómo probar la migración

```bash
cd wm_ai
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python -m spacy download es_core_news_sm
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

Ir a `http://localhost:8001/docs` y verificar que aparecen los 5 endpoints.
Probar `GET /health` → debe responder `{"status": "ok", "framework": "FastAPI"}`.
Probar `POST /ia/generar-diagrama` con el mismo body que funcionaba antes.

---

---

# AGENTE 2 — Deep Learning: Modelo de Cuellos de Botella con TensorFlow

## Contexto
El modelo actual usa RandomForest + GradientBoosting + IsolationForest (scikit-learn). Hay que reemplazarlo por una **red neuronal con TensorFlow/Keras**. Los datos de entrenamiento se generan sintéticamente igual que antes. El modelo detecta si un nodo es cuello de botella y predice el riesgo de demora.

## Arquitectura del modelo

```
Entrada: 10 features por nodo
  - tiempo_promedio_minutos
  - cantidad_ejecuciones_activas
  - tasa_rechazo
  - tiempo_espera_promedio_minutos
  - varianza_tiempo
  - porcentaje_carga (activas / capacidad_departamento)
  - hora_pico (0/1)
  - es_nodo_decision (0/1)
  - posicion_en_flujo (0.0 a 1.0)
  - dias_desde_ultima_ejecucion

Salida: 
  - probabilidad_cuello_botella (0.0 a 1.0)
  - categoria_riesgo (0=BAJO, 1=MEDIO, 2=ALTO, 3=CRITICO)
```

## Paso 1 — train_model.py (reemplazar completamente)

```python
"""
Script de entrenamiento del modelo de cuellos de botella.
Usa TensorFlow/Keras con datos sintéticos realistas.
Ejecutar: python train_model.py
"""
import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow import keras
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score
import pickle
import json

# ── 1. Generar datos sintéticos ───────────────────────────────────────────────
np.random.seed(42)
N = 5000  # muestras de entrenamiento

def generar_datos_sinteticos(n: int):
    datos = []
    for _ in range(n):
        # Nodo normal
        tiempo_prom = np.random.exponential(30)  # minutos
        ejecuciones_activas = np.random.poisson(3)
        tasa_rechazo = np.random.beta(2, 8)       # sesgado hacia bajo
        tiempo_espera = np.random.exponential(15)
        varianza = np.random.exponential(10)
        porcentaje_carga = np.random.uniform(0, 1)
        hora_pico = np.random.binomial(1, 0.3)
        es_decision = np.random.binomial(1, 0.2)
        posicion_flujo = np.random.uniform(0, 1)
        dias_ultima = np.random.exponential(2)

        # Etiqueta basada en reglas con ruido
        score_riesgo = (
            (tiempo_prom > 60) * 0.3 +
            (ejecuciones_activas > 5) * 0.25 +
            (tasa_rechazo > 0.3) * 0.2 +
            (tiempo_espera > 30) * 0.15 +
            (porcentaje_carga > 0.8) * 0.1
        )
        score_riesgo += np.random.normal(0, 0.05)  # ruido

        if score_riesgo > 0.7:
            label = 3   # CRITICO
        elif score_riesgo > 0.5:
            label = 2   # ALTO
        elif score_riesgo > 0.3:
            label = 1   # MEDIO
        else:
            label = 0   # BAJO

        datos.append([
            tiempo_prom, ejecuciones_activas, tasa_rechazo,
            tiempo_espera, varianza, porcentaje_carga,
            hora_pico, es_decision, posicion_flujo, dias_ultima,
            label
        ])

    cols = [
        'tiempo_promedio_minutos', 'cantidad_ejecuciones_activas',
        'tasa_rechazo', 'tiempo_espera_promedio_minutos', 'varianza_tiempo',
        'porcentaje_carga', 'hora_pico', 'es_nodo_decision',
        'posicion_en_flujo', 'dias_desde_ultima_ejecucion', 'label'
    ]
    return pd.DataFrame(datos, columns=cols)

df = generar_datos_sinteticos(N)
X = df.drop('label', axis=1).values
y = df['label'].values

# ── 2. Preprocesamiento ───────────────────────────────────────────────────────
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

scaler = StandardScaler()
X_train_sc = scaler.fit_transform(X_train)
X_test_sc = scaler.transform(X_test)

# ── 3. Definir la red neuronal ────────────────────────────────────────────────
def crear_modelo():
    modelo = keras.Sequential([
        keras.layers.Dense(64, activation='relu', input_shape=(10,)),
        keras.layers.BatchNormalization(),
        keras.layers.Dropout(0.3),
        keras.layers.Dense(128, activation='relu'),
        keras.layers.BatchNormalization(),
        keras.layers.Dropout(0.3),
        keras.layers.Dense(64, activation='relu'),
        keras.layers.Dropout(0.2),
        keras.layers.Dense(32, activation='relu'),
        keras.layers.Dense(4, activation='softmax')  # 4 clases
    ])
    modelo.compile(
        optimizer=keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return modelo

modelo = crear_modelo()
modelo.summary()

# ── 4. Entrenar ───────────────────────────────────────────────────────────────
early_stop = keras.callbacks.EarlyStopping(
    monitor='val_loss', patience=10, restore_best_weights=True
)
reduce_lr = keras.callbacks.ReduceLROnPlateau(
    monitor='val_loss', factor=0.5, patience=5
)

historia = modelo.fit(
    X_train_sc, y_train,
    epochs=100,
    batch_size=64,
    validation_split=0.2,
    callbacks=[early_stop, reduce_lr],
    verbose=1
)

# ── 5. Evaluar ────────────────────────────────────────────────────────────────
y_pred = np.argmax(modelo.predict(X_test_sc), axis=1)
print("\n📊 Reporte de clasificación:")
print(classification_report(y_test, y_pred,
      target_names=['BAJO', 'MEDIO', 'ALTO', 'CRITICO']))

# AUC one-vs-rest
y_pred_proba = modelo.predict(X_test_sc)
auc = roc_auc_score(y_test, y_pred_proba, multi_class='ovr')
print(f"AUC-ROC (OvR): {auc:.4f}")

# ── 6. Guardar modelo y scaler ────────────────────────────────────────────────
modelo.save('modelo_cuello_botella.h5')
with open('scaler_cuello_botella.pkl', 'wb') as f:
    pickle.dump(scaler, f)

metadata = {
    "version": "v4_tensorflow",
    "features": [
        'tiempo_promedio_minutos', 'cantidad_ejecuciones_activas',
        'tasa_rechazo', 'tiempo_espera_promedio_minutos', 'varianza_tiempo',
        'porcentaje_carga', 'hora_pico', 'es_nodo_decision',
        'posicion_en_flujo', 'dias_desde_ultima_ejecucion'
    ],
    "clases": ["BAJO", "MEDIO", "ALTO", "CRITICO"],
    "auc_roc": round(auc, 4),
    "samples_train": len(X_train)
}
with open('modelo_metadata.json', 'w') as f:
    json.dump(metadata, f, indent=2)

print("\n✅ Modelo guardado: modelo_cuello_botella.h5")
print("✅ Scaler guardado: scaler_cuello_botella.pkl")
print("✅ Metadata guardada: modelo_metadata.json")
```

## Paso 2 — analisis_service.py (reemplazar con TensorFlow)

```python
"""
Servicio de análisis de cuellos de botella usando TensorFlow/Keras.
"""
import numpy as np
import pickle
import json
import os
import tensorflow as tf
from tensorflow import keras

# Cargar modelo al iniciar el módulo
_modelo = None
_scaler = None
_metadata = None

SEVERIDADES = ["BAJA", "MEDIA", "ALTA", "CRITICA"]
UMBRAL_REENTRENAR = "v4_tensorflow"

def _cargar_modelo():
    global _modelo, _scaler, _metadata
    try:
        _modelo = keras.models.load_model('modelo_cuello_botella.h5')
        with open('scaler_cuello_botella.pkl', 'rb') as f:
            _scaler = pickle.load(f)
        with open('modelo_metadata.json', 'r') as f:
            _metadata = json.load(f)

        if _metadata.get("version") != UMBRAL_REENTRENAR:
            raise ValueError("Versión de modelo desactualizada")

        print(f"✅ Modelo TensorFlow cargado (v{_metadata['version']})")
    except Exception as e:
        print(f"⚠️ Modelo no encontrado o desactualizado: {e}")
        print("🔄 Entrenando nuevo modelo...")
        import subprocess
        subprocess.run(["python", "train_model.py"], check=True)
        _cargar_modelo()

_cargar_modelo()

def _extraer_features(nodo: dict) -> list:
    """Extrae los 10 features del nodo para el modelo."""
    return [
        float(nodo.get('tiempo_promedio_minutos', 0)),
        float(nodo.get('cantidad_ejecuciones_activas', 0)),
        float(nodo.get('tasa_rechazo', 0)),
        float(nodo.get('tiempo_espera_promedio_minutos', 0)),
        float(nodo.get('varianza_tiempo', 0)),
        float(nodo.get('porcentaje_carga', 0)),
        float(nodo.get('hora_pico', 0)),
        float(nodo.get('es_nodo_decision', 0)),
        float(nodo.get('posicion_en_flujo', 0.5)),
        float(nodo.get('dias_desde_ultima_ejecucion', 1)),
    ]

def _generar_sugerencias(severidad_idx: int, features: list) -> list:
    """Genera sugerencias contextuales según el perfil del nodo."""
    sugerencias = []
    tiempo_prom, ejecuciones_activas, tasa_rechazo, tiempo_espera = features[:4]

    if ejecuciones_activas > 5:
        sugerencias.append("Aumentar la capacidad del departamento asignado a este nodo.")
    if tasa_rechazo > 0.3:
        sugerencias.append("Revisar los criterios de aprobación — la tasa de rechazo es alta.")
    if tiempo_espera > 30:
        sugerencias.append("El tiempo de espera antes de procesar es elevado. Considerar notificaciones automáticas.")
    if tiempo_prom > 60:
        sugerencias.append("El tiempo de procesamiento supera 1 hora. Evaluar automatización parcial.")
    if severidad_idx >= 2:
        sugerencias.append("Considerar la paralelización de este nodo con otros de menor carga.")
    if not sugerencias:
        sugerencias.append("Nodo operando dentro de parámetros normales.")
    return sugerencias

def analizar_cuellos_botella(nodos_metricas: list) -> list:
    """
    Recibe lista de métricas por nodo y retorna análisis ordenado por riesgo.
    """
    if not nodos_metricas:
        return []

    features_matrix = [_extraer_features(n) for n in nodos_metricas]
    X = np.array(features_matrix)
    X_scaled = _scaler.transform(X)

    # Predicción con la red neuronal
    probabilidades = _modelo.predict(X_scaled, verbose=0)  # shape: (n, 4)
    clases_pred = np.argmax(probabilidades, axis=1)

    resultados = []
    for i, nodo in enumerate(nodos_metricas):
        severidad_idx = int(clases_pred[i])
        prob_max = float(probabilidades[i][severidad_idx])
        prob_cuello = float(probabilidades[i][2] + probabilidades[i][3])  # P(ALTO) + P(CRITICO)

        resultados.append({
            "nodoId": nodo.get("nodoId"),
            "nodoNombre": nodo.get("nodoNombre"),
            "severidad": SEVERIDADES[severidad_idx],
            "probabilidad_cuello_botella": round(prob_cuello, 4),
            "confianza": round(prob_max, 4),
            "distribucion_probabilidades": {
                "BAJA": round(float(probabilidades[i][0]), 4),
                "MEDIA": round(float(probabilidades[i][1]), 4),
                "ALTA": round(float(probabilidades[i][2]), 4),
                "CRITICA": round(float(probabilidades[i][3]), 4),
            },
            "sugerencias": _generar_sugerencias(severidad_idx, features_matrix[i]),
            "modelo_version": _metadata.get("version")
        })

    # Ordenar por probabilidad de cuello de botella descendente
    resultados.sort(key=lambda x: x['probabilidad_cuello_botella'], reverse=True)
    return resultados
```

## Paso 3 — Cómo probar

```bash
# 1. Entrenar el modelo (primera vez o si no existe)
python train_model.py
# Debe generar modelo_cuello_botella.h5 + scaler_cuello_botella.pkl + modelo_metadata.json

# 2. Iniciar el servidor
uvicorn main:app --port 8001 --reload

# 3. Probar desde Swagger: POST /ia/generar-analisis
# Body de ejemplo:
{
  "nodos": [
    {
      "nodoId": "n1",
      "nodoNombre": "Revisión Legal",
      "tiempo_promedio_minutos": 90,
      "cantidad_ejecuciones_activas": 8,
      "tasa_rechazo": 0.45,
      "tiempo_espera_promedio_minutos": 45,
      "varianza_tiempo": 30,
      "porcentaje_carga": 0.85,
      "hora_pico": 1,
      "es_nodo_decision": 1,
      "posicion_en_flujo": 0.6,
      "dias_desde_ultima_ejecucion": 0.5
    }
  ]
}
```

**Resultado esperado:** nodo con severidad ALTA o CRITICA, probabilidad > 0.7.

---

---

# AGENTE 3 — NLP con Deep Learning: Reportes Dinámicos

## Contexto
El admin (jefe de políticas) puede hablar o escribir en lenguaje natural: *"quiero ver cuántos trámites se completaron este mes ordenados por departamento"*. El sistema interpreta esto y genera el reporte en el formato pedido (Excel, PDF, Word, pantalla).

## Modelo NLP elegido

Usamos **`sentence-transformers`** (modelo `paraphrase-multilingual-MiniLM-L12-v2`) para entender el intent + entidades de la consulta, combinado con **Groq (Llama 3.1)** para generar la consulta estructurada. Esto es Deep Learning porque los sentence transformers son redes transformer preentrenadas.

El flujo:
```
Texto del admin
      ↓
sentence-transformers → embedding semántico
      ↓
Clasificar intent (qué tipo de reporte quiere)
      ↓
Groq → genera JSON con { campos, filtros, agrupacion, formato, ordenamiento }
      ↓
Backend Java ejecuta la consulta real en MongoDB
      ↓
Resultado → exportar según formato pedido
```

## Paso 1 — requirements.txt (agregar)

```txt
sentence-transformers==2.7.0
```

## Paso 2 — app/services/reporte_service.py *(crear)*

```python
"""
Servicio de generación de reportes dinámicos usando NLP + Deep Learning.
Interpreta consultas en lenguaje natural (texto o voz transcrita) y genera
una especificación estructurada del reporte solicitado.
"""
from groq import Groq
from app.core.config import settings
from sentence_transformers import SentenceTransformer
import numpy as np
import json

# Cargar modelo de embeddings al iniciar
_embedding_model = None

def _get_embedding_model():
    global _embedding_model
    if _embedding_model is None:
        print("📥 Cargando modelo de embeddings (primera vez puede demorar)...")
        _embedding_model = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')
        print("✅ Modelo de embeddings listo")
    return _embedding_model

# Catálogo de tipos de reporte disponibles (con embeddings precalculados)
TIPOS_REPORTE = [
    {"tipo": "TRAMITES_POR_ESTADO",      "descripcion": "cantidad de trámites por estado activos completados rechazados"},
    {"tipo": "TRAMITES_POR_POLITICA",    "descripcion": "cuántos trámites hay por política de negocio"},
    {"tipo": "TRAMITES_POR_PERIODO",     "descripcion": "trámites completados por día semana mes año período de tiempo"},
    {"tipo": "TIEMPO_PROMEDIO_NODO",     "descripcion": "tiempo promedio de procesamiento por nodo departamento"},
    {"tipo": "CUELLOS_BOTELLA",          "descripcion": "nodos con demoras cuellos de botella análisis de riesgo"},
    {"tipo": "CLIENTES_MAS_ACTIVOS",     "descripcion": "qué cliente hace más peticiones usuario más activo"},
    {"tipo": "TASA_RECHAZO",             "descripcion": "tasa de rechazo aprobaciones rechazos por nodo"},
    {"tipo": "RENDIMIENTO_DEPARTAMENTO", "descripcion": "rendimiento productividad por departamento"},
]

SYSTEM_PROMPT_REPORTE = """
Eres un asistente especializado en interpretar consultas de reportes para un sistema de gestión de trámites.

Cuando el usuario describa un reporte, debes responder ÚNICAMENTE con un JSON con esta estructura exacta:
{
  "tipo_reporte": "TRAMITES_POR_ESTADO|TRAMITES_POR_POLITICA|TRAMITES_POR_PERIODO|TIEMPO_PROMEDIO_NODO|CUELLOS_BOTELLA|CLIENTES_MAS_ACTIVOS|TASA_RECHAZO|RENDIMIENTO_DEPARTAMENTO",
  "filtros": {
    "fecha_inicio": "YYYY-MM-DD o null",
    "fecha_fin": "YYYY-MM-DD o null",
    "politicaId": "null o especificada",
    "departamentoNombre": "null o especificado",
    "estado": "null o ACTIVO|COMPLETADO|RECHAZADO"
  },
  "agrupacion": "DIA|SEMANA|MES|POLITICA|DEPARTAMENTO|ESTADO|null",
  "ordenamiento": "ASCENDENTE|DESCENDENTE",
  "ordenar_por": "cantidad|tiempo_promedio|tasa_rechazo|null",
  "limite": 10,
  "formato_exportacion": "PANTALLA|EXCEL|PDF|WORD",
  "preguntas_faltantes": [],
  "descripcion_reporte": "descripción en español de qué va a mostrar el reporte"
}

Si falta información crítica (como el período de tiempo), agrégala en "preguntas_faltantes" como lista de strings.
Solo responde JSON, sin texto adicional.
"""

def interpretar_consulta_reporte(consulta: str) -> dict:
    """
    Interpreta una consulta en lenguaje natural y retorna la especificación del reporte.
    Si falta información, retorna preguntas_faltantes para pedirle al usuario.
    """
    model = _get_embedding_model()

    # 1. Clasificar el intent con embeddings semánticos
    embeddings_tipos = model.encode([t["descripcion"] for t in TIPOS_REPORTE])
    embedding_consulta = model.encode([consulta])

    # Similitud coseno
    similitudes = np.dot(embedding_consulta, embeddings_tipos.T)[0]
    similitudes = similitudes / (
        np.linalg.norm(embedding_consulta) * np.linalg.norm(embeddings_tipos, axis=1) + 1e-8
    )
    tipo_detectado = TIPOS_REPORTE[np.argmax(similitudes)]["tipo"]

    # 2. Groq genera los detalles estructurados
    cliente = Groq(api_key=settings.groq_api_key)
    prompt_usuario = f"""
    Tipo de reporte detectado automáticamente: {tipo_detectado}
    Consulta del usuario: "{consulta}"
    
    Genera la especificación JSON completa del reporte.
    """

    response = cliente.chat.completions.create(
        model="llama-3.1-8b-instant",
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT_REPORTE},
            {"role": "user", "content": prompt_usuario}
        ],
        max_tokens=500,
        temperature=0.1
    )

    raw = response.choices[0].message.content.strip()
    # Limpiar posibles backticks
    raw = raw.replace("```json", "").replace("```", "").strip()

    resultado = json.loads(raw)
    resultado["tipo_reporte_detectado_por_nlp"] = tipo_detectado
    resultado["similitud_confianza"] = round(float(np.max(similitudes)), 4)

    return resultado
```

## Paso 3 — app/routers/reportes.py *(crear)*

```python
from fastapi import APIRouter, HTTPException
from app.models.schemas import ReporteRequest, ReporteResponse
from app.services.reporte_service import interpretar_consulta_reporte

router = APIRouter()

@router.post("/generar-reporte", response_model=ReporteResponse)
async def generar_reporte(request: ReporteRequest):
    try:
        resultado = interpretar_consulta_reporte(request.consulta)
        return ReporteResponse(
            tipo_reporte=resultado.get("tipo_reporte", "DESCONOCIDO"),
            filtros=resultado.get("filtros", {}),
            agrupacion=resultado.get("agrupacion"),
            ordenamiento=resultado.get("ordenamiento", "DESCENDENTE"),
            ordenar_por=resultado.get("ordenar_por"),
            limite=resultado.get("limite", 10),
            formato_exportacion=resultado.get("formato_exportacion", "PANTALLA"),
            preguntas_faltantes=resultado.get("preguntas_faltantes", []),
            descripcion_reporte=resultado.get("descripcion_reporte", ""),
            confianza_nlp=resultado.get("similitud_confianza", 0.0)
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error al interpretar consulta: {str(e)}")
```

## Paso 4 — schemas.py (agregar)

```python
class ReporteRequest(BaseModel):
    consulta: str  # texto libre del admin: "quiero ver trámites del mes de mayo"

class ReporteResponse(BaseModel):
    tipo_reporte: str
    filtros: dict
    agrupacion: Optional[str]
    ordenamiento: str
    ordenar_por: Optional[str]
    limite: int
    formato_exportacion: str          # PANTALLA | EXCEL | PDF | WORD
    preguntas_faltantes: list[str]    # vacío si tiene todo, con preguntas si falta info
    descripcion_reporte: str
    confianza_nlp: float
```

## Paso 5 — Backend Java: ejecutar la consulta real

**Archivo:** `wm_backend/src/.../reporte/service/ReporteService.java` *(crear)*

El Python solo interpreta el lenguaje natural y retorna la especificación. El Java ejecuta la consulta real contra MongoDB y genera el archivo.

```java
// ReporteController.java
@PostMapping("/api/v1/reportes/generar")
public ResponseEntity<?> generarReporte(@RequestBody ReporteConsultaRequest request) {
    // 1. Llamar al Python para interpretar la consulta
    EspecificacionReporte spec = iaService.interpretarConsulta(request.getConsulta());

    // 2. Si hay preguntas faltantes, devolverlas al frontend
    if (!spec.getPreguntasFaltantes().isEmpty()) {
        return ResponseEntity.ok(Map.of(
            "estado", "PREGUNTAS_PENDIENTES",
            "preguntas", spec.getPreguntasFaltantes()
        ));
    }

    // 3. Ejecutar la consulta en MongoDB según el tipo de reporte
    List<Map<String, Object>> datos = reporteService.ejecutarConsulta(spec, empresaId);

    // 4. Exportar según formato
    return switch (spec.getFormatoExportacion()) {
        case "EXCEL" -> reporteExportService.exportarExcel(datos, spec);
        case "PDF"   -> reporteExportService.exportarPDF(datos, spec);
        case "WORD"  -> reporteExportService.exportarWord(datos, spec);
        default      -> ResponseEntity.ok(Map.of("datos", datos, "descripcion", spec.getDescripcionReporte()));
    };
}
```

**Para exportar Excel:** Apache POI (ya en Spring Boot si se agrega la dependencia)
**Para exportar PDF:** iText o JasperReports
**Para exportar Word:** Apache POI XWPF

```xml
<!-- pom.xml — agregar -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

## Paso 6 — Cómo probar

```bash
# POST /ia/generar-reporte
{
  "consulta": "quiero ver los trámites completados este mes ordenados por departamento"
}

# Resultado esperado:
{
  "tipo_reporte": "TRAMITES_POR_PERIODO",
  "filtros": { "fecha_inicio": "2026-05-01", "fecha_fin": "2026-05-31" },
  "agrupacion": "DEPARTAMENTO",
  "formato_exportacion": "PANTALLA",
  "preguntas_faltantes": [],
  "descripcion_reporte": "Muestra los trámites completados en mayo 2026 agrupados por departamento"
}
```

---

---

# AGENTE 4 — AWS S3 + Modelo de Datos Documental

## Paso 1 — pom.xml (agregar dependencias)

```xml
<!-- AWS S3 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.27</version>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>auth</artifactId>
    <version>2.25.27</version>
</dependency>
<!-- Apache POI para exportar Excel/Word -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

## Paso 2 — Variables de entorno (.env)

```
AWS_ACCESS_KEY_ID=tu_access_key_aqui
AWS_SECRET_ACCESS_KEY=tu_secret_key_aqui
AWS_REGION=us-east-1
AWS_S3_BUCKET=wm-documentos
```

## Paso 3 — AwsS3Config.java *(crear)*

```java
// wm_backend/src/.../config/AwsS3Config.java
@Configuration
public class AwsS3Config {

    @Value("${AWS_ACCESS_KEY_ID}")
    private String accessKey;

    @Value("${AWS_SECRET_ACCESS_KEY}")
    private String secretKey;

    @Value("${AWS_REGION:us-east-1}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
            .build();
    }
}
```

## Paso 4 — S3Service.java *(crear)*

```java
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;

    @Value("${AWS_S3_BUCKET}")
    private String bucket;

    /**
     * Sube un archivo a S3.
     * Estructura de key: {empresaId}/{politicaId}/{tramiteId}/{nombreArchivo}
     * Si tramiteId es null, sube a {empresaId}/{politicaId}/libre/{nombreArchivo}
     */
    public String subirArchivo(MultipartFile archivo, String empresaId,
                               String politicaId, String tramiteId) throws IOException {
        String key = construirKey(empresaId, politicaId, tramiteId, archivo.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(archivo.getContentType())
            .build();

        s3Client.putObject(request,
            RequestBody.fromInputStream(archivo.getInputStream(), archivo.getSize()));

        return obtenerUrlPublica(key);
    }

    public void eliminarArchivo(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build());
    }

    /** URL pre-firmada para descarga temporal (15 minutos) */
    public String generarUrlPresignada(String key) {
        S3Presigner presigner = S3Presigner.builder()
            .region(s3Client.serviceClientConfiguration().region())
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .getObjectRequest(r -> r.bucket(bucket).key(key))
            .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    private String construirKey(String empresaId, String politicaId,
                                 String tramiteId, String nombreArchivo) {
        String base = empresaId + "/" + politicaId + "/";
        base += (tramiteId != null ? tramiteId : "libre") + "/";
        return base + System.currentTimeMillis() + "_" + nombreArchivo;
    }

    private String obtenerUrlPublica(String key) {
        return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }
}
```

## Paso 5 — Modelos MongoDB para gestión documental

```java
// documento/model/Documento.java
@Document(collection = "documentos")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Documento {
    @Id private String id;
    private String empresaId;
    private String nombre;
    private String descripcion;
    private String tipoMime;
    private String urlArchivo;      // URL en S3
    private String s3Key;           // clave en S3 (para eliminar/presign)
    private Long tamanioBytes;
    private String carpetaId;       // null = raíz
    private String politicaId;
    private String tramiteId;
    private List<String> etiquetas;
    private Integer version;        // empieza en 1
    private List<VersionDocumento> historialVersiones;
    private PermisosDocumento permisos;
    private String creadoPorId;
    private LocalDateTime creadoEn;
    private LocalDateTime modificadoEn;
    private boolean eliminado;      // soft delete
}

// documento/model/VersionDocumento.java (clase embebida)
@Data
public class VersionDocumento {
    private Integer version;
    private String urlArchivo;
    private String s3Key;
    private LocalDateTime fechaSubida;
    private String subidoPorId;
    private String subidoPorNombre;
    private Long tamanioBytes;
}

// documento/model/PermisosDocumento.java (clase embebida)
@Data
public class PermisosDocumento {
    private List<String> puedenVer;       // userIds o roles: "ADMIN_GENERAL", "userId123"
    private List<String> puedenEditar;
    private List<String> puedenEliminar;
}

// documento/model/AuditoriaDocumento.java
@Document(collection = "auditoria_documentos")
@Data @Builder
public class AuditoriaDocumento {
    @Id private String id;
    private String documentoId;
    private String usuarioId;
    private String usuarioNombre;
    private String accion;    // SUBIO, DESCARGO, VIO, EDITO, ELIMINO, CAMBIO_PERMISOS
    private String detalles;
    private LocalDateTime fechaHora;
}
```

## Paso 6 — Cómo probar

```bash
# 1. Verificar que AWS S3 está configurado
curl -X POST http://localhost:8080/api/v1/archivos/upload \
  -F "file=@/ruta/al/archivo.pdf" \
  -F "empresaId=empresa1" \
  -F "politicaId=pol1"
# Debe retornar URL de S3

# 2. Verificar estructura de carpetas en S3:
# empresa1/pol1/libre/1234567890_archivo.pdf
```

---

---

# AGENTE 5 — API REST de Gestión Documental

## Paso 1 — DocumentoService.java *(crear)*

```java
@Service
@RequiredArgsConstructor
public class DocumentoService {

    private final DocumentoRepository documentoRepo;
    private final AuditoriaDocumentoRepository auditoriaRepo;
    private final S3Service s3Service;

    public DocumentoResponse subirDocumento(MultipartFile archivo,
                                             DocumentoRequest request,
                                             String usuarioId, String usuarioNombre) throws IOException {
        String url = s3Service.subirArchivo(archivo,
            request.getEmpresaId(), request.getPoliticaId(), request.getTramiteId());

        VersionDocumento primeraVersion = VersionDocumento.builder()
            .version(1)
            .urlArchivo(url)
            .fechaSubida(LocalDateTime.now())
            .subidoPorId(usuarioId)
            .subidoPorNombre(usuarioNombre)
            .tamanioBytes(archivo.getSize())
            .build();

        Documento doc = Documento.builder()
            .empresaId(request.getEmpresaId())
            .nombre(request.getNombre())
            .descripcion(request.getDescripcion())
            .tipoMime(archivo.getContentType())
            .urlArchivo(url)
            .tamanioBytes(archivo.getSize())
            .carpetaId(request.getCarpetaId())
            .politicaId(request.getPoliticaId())
            .tramiteId(request.getTramiteId())
            .etiquetas(request.getEtiquetas())
            .version(1)
            .historialVersiones(List.of(primeraVersion))
            .permisos(request.getPermisos())
            .creadoPorId(usuarioId)
            .creadoEn(LocalDateTime.now())
            .modificadoEn(LocalDateTime.now())
            .eliminado(false)
            .build();

        Documento guardado = documentoRepo.save(doc);
        registrarAuditoria(guardado.getId(), usuarioId, usuarioNombre, "SUBIO",
            "Versión 1 — " + archivo.getOriginalFilename());

        return mapToResponse(guardado);
    }

    public DocumentoResponse subirNuevaVersion(String documentoId, MultipartFile archivo,
                                                String usuarioId, String usuarioNombre) throws IOException {
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));

        verificarPermiso(doc, usuarioId, "EDITAR");

        String url = s3Service.subirArchivo(archivo,
            doc.getEmpresaId(), doc.getPoliticaId(), doc.getTramiteId());

        int nuevaVersion = doc.getVersion() + 1;
        VersionDocumento version = VersionDocumento.builder()
            .version(nuevaVersion)
            .urlArchivo(url)
            .fechaSubida(LocalDateTime.now())
            .subidoPorId(usuarioId)
            .subidoPorNombre(usuarioNombre)
            .tamanioBytes(archivo.getSize())
            .build();

        doc.getHistorialVersiones().add(version);
        doc.setVersion(nuevaVersion);
        doc.setUrlArchivo(url);
        doc.setModificadoEn(LocalDateTime.now());

        Documento actualizado = documentoRepo.save(doc);
        registrarAuditoria(doc.getId(), usuarioId, usuarioNombre, "SUBIO",
            "Nueva versión: v" + nuevaVersion);

        return mapToResponse(actualizado);
    }

    public List<DocumentoResponse> listar(String empresaId, String carpetaId,
                                           String politicaId, String tramiteId) {
        List<Documento> docs;
        if (tramiteId != null) {
            docs = documentoRepo.findByEmpresaIdAndTramiteIdAndEliminadoFalse(empresaId, tramiteId);
        } else if (politicaId != null) {
            docs = documentoRepo.findByEmpresaIdAndPoliticaIdAndEliminadoFalse(empresaId, politicaId);
        } else {
            docs = documentoRepo.findByEmpresaIdAndCarpetaIdAndEliminadoFalse(empresaId, carpetaId);
        }
        return docs.stream().map(this::mapToResponse).toList();
    }

    private void verificarPermiso(Documento doc, String usuarioId, String tipo) {
        List<String> permitidos = switch (tipo) {
            case "VER"     -> doc.getPermisos().getPuedenVer();
            case "EDITAR"  -> doc.getPermisos().getPuedenEditar();
            case "ELIMINAR"-> doc.getPermisos().getPuedenEliminar();
            default -> List.of();
        };
        boolean tienePermiso = permitidos.contains(usuarioId) ||
                               permitidos.contains("ADMIN_GENERAL") ||
                               permitidos.contains("TODOS");
        if (!tienePermiso) throw new UnauthorizedException("Sin permiso para " + tipo);
    }

    private void registrarAuditoria(String docId, String userId,
                                    String nombre, String accion, String detalle) {
        auditoriaRepo.save(AuditoriaDocumento.builder()
            .documentoId(docId)
            .usuarioId(userId)
            .usuarioNombre(nombre)
            .accion(accion)
            .detalles(detalle)
            .fechaHora(LocalDateTime.now())
            .build());
    }
}
```

## Paso 2 — DocumentoController.java *(crear)*

```java
@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentoResponse> subir(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart("datos") DocumentoRequest request,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        return ResponseEntity.ok(documentoService.subirDocumento(
            archivo, request, user.getUsername(), /* nombre */ ""));
    }

    @GetMapping
    public ResponseEntity<List<DocumentoResponse>> listar(
            @RequestParam String empresaId,
            @RequestParam(required = false) String carpetaId,
            @RequestParam(required = false) String politicaId,
            @RequestParam(required = false) String tramiteId) {
        return ResponseEntity.ok(documentoService.listar(empresaId, carpetaId, politicaId, tramiteId));
    }

    @PostMapping("/{id}/version")
    public ResponseEntity<DocumentoResponse> nuevaVersion(
            @PathVariable String id,
            @RequestPart("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        return ResponseEntity.ok(documentoService.subirNuevaVersion(id, archivo,
            user.getUsername(), ""));
    }

    @GetMapping("/{id}/auditoria")
    public ResponseEntity<List<AuditoriaDocumento>> auditoria(@PathVariable String id) {
        return ResponseEntity.ok(documentoService.obtenerAuditoria(id));
    }

    @PutMapping("/{id}/permisos")
    public ResponseEntity<DocumentoResponse> cambiarPermisos(
            @PathVariable String id,
            @RequestBody PermisosDocumento permisos,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(documentoService.cambiarPermisos(id, permisos, user.getUsername()));
    }
}
```

## Paso 3 — Cómo probar

```bash
# Subir documento
POST /api/v1/documentos/upload
  multipart: archivo=file.pdf, datos={"nombre":"Contrato","politicaId":"pol1","empresaId":"emp1",...}

# Listar documentos de un trámite
GET /api/v1/documentos?empresaId=emp1&tramiteId=tram1

# Ver auditoría
GET /api/v1/documentos/{id}/auditoria

# Subir nueva versión
POST /api/v1/documentos/{id}/version
  multipart: archivo=contrato_v2.pdf
```

---

---

# AGENTE 6 — Edición Colaborativa con OnlyOffice

## Decisión técnica
Usamos **OnlyOffice Document Server** (Docker) como editor colaborativo embebido. Es open source, soporta Word/Excel/PowerPoint, y permite edición simultánea en tiempo real como Google Docs. No requiere inventar nada desde cero.

## Paso 1 — docker-compose.yml (agregar al proyecto)

Crear `docker-compose.yml` en la raíz del proyecto (junto a los repos):

```yaml
version: '3.8'
services:
  onlyoffice:
    image: onlyoffice/documentserver:latest
    container_name: wm-onlyoffice
    ports:
      - "8088:80"
    environment:
      - JWT_ENABLED=true
      - JWT_SECRET=wm-onlyoffice-secret-key-2026
    volumes:
      - onlyoffice_data:/var/www/onlyoffice/Data
      - onlyoffice_log:/var/log/onlyoffice
    restart: unless-stopped

volumes:
  onlyoffice_data:
  onlyoffice_log:
```

```bash
docker-compose up -d onlyoffice
# Verificar: http://localhost:8088 → debe aparecer la pantalla de OnlyOffice
```

## Paso 2 — Backend Java: OnlyOffice callback endpoint

OnlyOffice necesita un callback para guardar el documento cuando el usuario termina de editar.

**Archivo:** `wm_backend/src/.../documento/controller/OnlyOfficeCallbackController.java` *(crear)*

```java
@RestController
@RequestMapping("/api/v1/onlyoffice")
@RequiredArgsConstructor
public class OnlyOfficeCallbackController {

    private final DocumentoService documentoService;
    private final S3Service s3Service;

    /**
     * OnlyOffice llama a este endpoint cuando termina la sesión de edición.
     * Status 2 = documento guardado. Se descarga el archivo editado y se sube como nueva versión a S3.
     */
    @PostMapping("/callback/{documentoId}")
    public ResponseEntity<Map<String, Integer>> callback(
            @PathVariable String documentoId,
            @RequestBody OnlyOfficeCallbackDTO body) throws Exception {

        if (body.getStatus() == 2) { // 2 = listo para guardar
            // Descargar el archivo desde la URL que da OnlyOffice
            byte[] contenido = descargarDesdeUrl(body.getUrl());
            // Subir como nueva versión a S3
            documentoService.guardarVersionDesdeBytes(documentoId, contenido, "Sistema OnlyOffice");
        }
        return ResponseEntity.ok(Map.of("error", 0)); // 0 = OK para OnlyOffice
    }

    private byte[] descargarDesdeUrl(String url) throws Exception {
        try (var stream = new java.net.URL(url).openStream()) {
            return stream.readAllBytes();
        }
    }
}
```

## Paso 3 — Backend Java: generar configuración para el editor

```java
// En DocumentoService.java — agregar este método
public Map<String, Object> generarConfigOnlyOffice(String documentoId, String usuarioId,
                                                    String usuarioNombre, String modo) {
    Documento doc = documentoRepo.findById(documentoId)
        .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado"));

    String callbackUrl = "http://TU_BACKEND_URL/api/v1/onlyoffice/callback/" + documentoId;

    return Map.of(
        "documentType", detectarTipoDoc(doc.getTipoMime()),
        "document", Map.of(
            "fileType", extraerExtension(doc.getNombre()),
            "key", documentoId + "_v" + doc.getVersion(),  // key única por versión
            "title", doc.getNombre(),
            "url", doc.getUrlArchivo()
        ),
        "editorConfig", Map.of(
            "callbackUrl", callbackUrl,
            "mode", modo,  // "edit" o "view"
            "user", Map.of(
                "id", usuarioId,
                "name", usuarioNombre
            ),
            "lang", "es"
        )
    );
}

private String detectarTipoDoc(String mime) {
    if (mime == null) return "word";
    return switch (mime) {
        case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
             "application/vnd.ms-excel" -> "cell";
        case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "slide";
        default -> "word";
    };
}
```

## Paso 4 — Frontend Angular: embeber el editor

**Archivo:** `wm_frontend/src/app/modules/admin/pages/documentos/editor-onlyoffice/editor-onlyoffice.component.ts` *(crear)*

```typescript
@Component({
  selector: 'app-editor-onlyoffice',
  template: `
    <div id="onlyoffice-container" style="width:100%; height:85vh;"></div>
  `
})
export class EditorOnlyofficeComponent implements OnInit, OnDestroy {
  @Input() documentoId!: string;
  @Input() modo: 'edit' | 'view' = 'view';
  private editor: any;

  constructor(private documentoService: DocumentoService) {}

  ngOnInit(): void {
    // Cargar el script de OnlyOffice dinámicamente
    const script = document.createElement('script');
    script.src = 'http://localhost:8088/web-apps/apps/api/documents/api.js';
    script.onload = () => this.inicializarEditor();
    document.head.appendChild(script);
  }

  private inicializarEditor(): void {
    this.documentoService.obtenerConfigOnlyOffice(this.documentoId, this.modo)
      .subscribe(config => {
        this.editor = new (window as any).DocsAPI.DocEditor('onlyoffice-container', {
          ...config,
          height: '100%',
          width: '100%',
          events: {
            onDocumentStateChange: (e: any) => {
              if (e.data) console.log('Documento modificado');
            }
          }
        });
      });
  }

  ngOnDestroy(): void {
    if (this.editor) this.editor.destroyEditor();
  }
}
```

## Paso 5 — Cómo probar la edición colaborativa

1. `docker-compose up -d onlyoffice` → esperar que levante (30-60 segundos)
2. Subir un archivo `.docx` desde la UI
3. Abrir el documento en el editor → debe abrirse en OnlyOffice embebido
4. Abrir el mismo documento en otro navegador con otro usuario
5. Ambos deben ver los cambios en tiempo real
6. Al cerrar la sesión, OnlyOffice llama al callback → se guarda como nueva versión en S3

---

---

# AGENTE 7 — Frontend Angular: Módulo Documental + PWA

## Paso 1 — Instalar dependencias

```bash
cd wm_frontend
npm install ng2-pdf-viewer     # visor de PDFs
npm install @angular/pwa       # PWA support
ng add @angular/pwa            # configura automáticamente el service worker
```

## Paso 2 — Módulo de documentos

**Estructura a crear:**
```
src/app/modules/admin/pages/documentos/
├── documentos.component.ts / .html / .scss
├── subir-documento/
│   └── subir-documento.component.ts / .html
├── ver-documento/
│   └── ver-documento.component.ts / .html
├── editor-onlyoffice/
│   └── editor-onlyoffice.component.ts / .html
└── auditoria-documento/
    └── auditoria-documento.component.ts / .html
```

**`documentos.component.html` — layout principal:**
```html
<div class="documentos-layout">
  <!-- Panel izquierdo: árbol de carpetas -->
  <mat-tree [dataSource]="carpetasDataSource" [treeControl]="treeControl" class="carpetas-tree">
    <mat-tree-node *matTreeNodeDef="let carpeta" matTreeNodePadding>
      <button mat-icon-button (click)="seleccionarCarpeta(carpeta)">
        <mat-icon>folder</mat-icon>
      </button>
      {{ carpeta.nombre }}
    </mat-tree-node>
  </mat-tree>

  <!-- Panel derecho: lista de documentos -->
  <div class="documentos-lista">
    <div class="acciones-header">
      <button mat-raised-button color="primary" (click)="abrirSubirDocumento()">
        <mat-icon>upload</mat-icon> Subir documento
      </button>
      <mat-form-field>
        <input matInput placeholder="Buscar..." [(ngModel)]="filtroTexto">
      </mat-form-field>
    </div>

    <mat-table [dataSource]="documentosFiltrados">
      <ng-container matColumnDef="nombre">
        <mat-header-cell *matHeaderCellDef>Nombre</mat-header-cell>
        <mat-cell *matCellDef="let doc">
          <mat-icon>{{ iconoPorTipo(doc.tipoMime) }}</mat-icon>
          {{ doc.nombre }}
          <mat-chip class="version-chip">v{{ doc.version }}</mat-chip>
        </mat-cell>
      </ng-container>

      <ng-container matColumnDef="subidoPor">
        <mat-header-cell *matHeaderCellDef>Subido por</mat-header-cell>
        <mat-cell *matCellDef="let doc">{{ doc.creadoPorNombre }}</mat-cell>
      </ng-container>

      <ng-container matColumnDef="fecha">
        <mat-header-cell *matHeaderCellDef>Fecha</mat-header-cell>
        <mat-cell *matCellDef="let doc">{{ doc.creadoEn | date:'dd/MM/yyyy HH:mm' }}</mat-cell>
      </ng-container>

      <ng-container matColumnDef="acciones">
        <mat-header-cell *matHeaderCellDef>Acciones</mat-header-cell>
        <mat-cell *matCellDef="let doc">
          <button mat-icon-button (click)="verDocumento(doc)" matTooltip="Ver">
            <mat-icon>visibility</mat-icon>
          </button>
          <button mat-icon-button (click)="editarDocumento(doc)" matTooltip="Editar">
            <mat-icon>edit</mat-icon>
          </button>
          <button mat-icon-button (click)="verAuditoria(doc)" matTooltip="Auditoría">
            <mat-icon>history</mat-icon>
          </button>
          <button mat-icon-button (click)="descargar(doc)" matTooltip="Descargar">
            <mat-icon>download</mat-icon>
          </button>
        </mat-cell>
      </ng-container>

      <mat-header-row *matHeaderRowDef="columnas"></mat-header-row>
      <mat-row *matRowDef="let row; columns: columnas;"></mat-row>
    </mat-table>
  </div>
</div>
```

## Paso 3 — Configurar PWA (Progressive Web App)

`ng add @angular/pwa` ya crea el `ngsw-config.json`. Solo hay que configurar qué cachear:

```json
// ngsw-config.json — modificar assetGroups y dataGroups
{
  "index": "/index.html",
  "assetGroups": [
    {
      "name": "app",
      "installMode": "prefetch",
      "resources": {
        "files": ["/favicon.ico", "/index.html", "/*.css", "/*.js"]
      }
    }
  ],
  "dataGroups": [
    {
      "name": "api-politicas",
      "urls": ["/api/v1/politicas/**"],
      "cacheConfig": { "strategy": "freshness", "maxSize": 100, "maxAge": "1h", "timeout": "3s" }
    },
    {
      "name": "api-tramites",
      "urls": ["/api/v1/tramites/**"],
      "cacheConfig": { "strategy": "freshness", "maxSize": 100, "maxAge": "30m", "timeout": "3s" }
    }
  ]
}
```

```typescript
// app.module.ts — registrar Service Worker
import { ServiceWorkerModule } from '@angular/service-worker';
import { environment } from '../environments/environment';

@NgModule({
  imports: [
    ServiceWorkerModule.register('ngsw-worker.js', {
      enabled: environment.production,
      registrationStrategy: 'registerWhenStable:30000'
    })
  ]
})
```

## Paso 4 — Cómo probar el módulo documental

1. Ir a `/admin/documentos`
2. Subir un PDF → debe aparecer en la lista con v1
3. Click en "Ver" → debe abrirse el visor inline de PDF
4. Subir nueva versión → debe mostrar v2, el historial debe tener v1 y v2
5. Click en "Auditoría" → debe mostrar quién vio y subió el documento
6. Click en "Editar" → debe abrirse OnlyOffice embebido
7. Abrir el mismo doc en otro navegador → ambos deben ver los cambios en tiempo real

**Probar PWA:**
```bash
ng build --prod
npx http-server dist/wm_frontend -p 4200
# Abrir DevTools → Application → Service Workers → debe aparecer registrado
# Simular offline → la app debe seguir funcionando con los datos cacheados
```

---

---

# AGENTE 8 — Frontend Angular: Reportes Dinámicos + Dashboard IA

## Paso 1 — Instalar dependencias

```bash
npm install ng2-charts chart.js   # gráficos
npm install jspdf html2canvas     # exportar PDF
npm install xlsx                  # exportar Excel
```

## Paso 2 — Componente de Reportes Dinámicos

**Archivo:** `src/app/modules/admin/pages/reportes-dinamicos/reportes-dinamicos.component.ts`

```typescript
@Component({...})
export class ReportesDinamicosComponent {
  consulta = '';
  escuchando = false;
  estado: 'idle' | 'procesando' | 'preguntas' | 'resultado' = 'idle';
  preguntasFaltantes: string[] = [];
  respuestasUsuario: { [key: string]: string } = {};
  datosReporte: any[] = [];
  descripcionReporte = '';
  formatoSeleccionado = 'PANTALLA';

  private recognition: any;

  constructor(private reporteService: ReporteService) {
    // Inicializar Web Speech API
    const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SR) {
      this.recognition = new SR();
      this.recognition.lang = 'es-BO';
      this.recognition.continuous = false;
      this.recognition.onresult = (e: any) => {
        this.consulta = e.results[0][0].transcript;
        this.escuchando = false;
      };
    }
  }

  grabar(): void {
    this.escuchando = true;
    this.recognition.start();
  }

  generarReporte(): void {
    if (!this.consulta.trim()) return;
    this.estado = 'procesando';

    // 1. Interpretar con IA
    this.reporteService.interpretarConsulta(this.consulta).subscribe({
      next: (spec) => {
        if (spec.preguntasFaltantes?.length > 0) {
          this.preguntasFaltantes = spec.preguntasFaltantes;
          this.estado = 'preguntas';
        } else {
          this.ejecutarReporte(spec);
        }
      },
      error: () => this.estado = 'idle'
    });
  }

  responderPreguntas(): void {
    // Agregar las respuestas a la consulta y volver a generar
    const consultaCompleta = this.consulta + ' ' +
      Object.entries(this.respuestasUsuario).map(([k, v]) => v).join(' ');
    this.consulta = consultaCompleta;
    this.preguntasFaltantes = [];
    this.estado = 'procesando';
    this.generarReporte();
  }

  private ejecutarReporte(spec: any): void {
    this.reporteService.ejecutarReporte(spec).subscribe({
      next: (resultado) => {
        this.datosReporte = resultado.datos;
        this.descripcionReporte = resultado.descripcion;
        this.estado = 'resultado';
      }
    });
  }

  exportarExcel(): void {
    // XLSX export
    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.json_to_sheet(this.datosReporte);
    XLSX.utils.book_append_sheet(wb, ws, 'Reporte');
    XLSX.writeFile(wb, 'reporte_workflowmanager.xlsx');
  }

  exportarPDF(): void {
    const elemento = document.getElementById('reporte-contenido')!;
    html2canvas(elemento).then(canvas => {
      const pdf = new jsPDF('p', 'mm', 'a4');
      const imgData = canvas.toDataURL('image/png');
      pdf.addImage(imgData, 'PNG', 10, 10, 190, 0);
      pdf.save('reporte_workflowmanager.pdf');
    });
  }
}
```

**Template:**
```html
<div class="reportes-dinamicos">
  <h2>Generador de Reportes con IA</h2>

  <!-- Input de consulta -->
  <mat-card class="consulta-card">
    <mat-form-field appearance="outline" style="width:100%">
      <mat-label>Describe el reporte que quieres</mat-label>
      <textarea matInput [(ngModel)]="consulta" rows="3"
        placeholder="Ej: Muéstrame los trámites completados este mes ordenados por departamento"></textarea>
    </mat-form-field>
    <div class="botones-consulta">
      <button mat-icon-button color="warn" (click)="grabar()" [class.grabando]="escuchando">
        <mat-icon>{{ escuchando ? 'mic' : 'mic_none' }}</mat-icon>
      </button>
      <button mat-raised-button color="primary" (click)="generarReporte()" [disabled]="estado === 'procesando'">
        <mat-spinner *ngIf="estado === 'procesando'" diameter="20"></mat-spinner>
        <span *ngIf="estado !== 'procesando'">Generar Reporte</span>
      </button>
    </div>
  </mat-card>

  <!-- Preguntas faltantes -->
  <mat-card *ngIf="estado === 'preguntas'" class="preguntas-card">
    <mat-card-title>Necesito más información</mat-card-title>
    <div *ngFor="let pregunta of preguntasFaltantes; let i = index">
      <p>{{ pregunta }}</p>
      <mat-form-field>
        <input matInput [(ngModel)]="respuestasUsuario[i]">
      </mat-form-field>
    </div>
    <button mat-raised-button color="accent" (click)="responderPreguntas()">Continuar</button>
  </mat-card>

  <!-- Resultado del reporte -->
  <div *ngIf="estado === 'resultado'" id="reporte-contenido">
    <mat-card class="descripcion-card">
      <p><mat-icon>info</mat-icon> {{ descripcionReporte }}</p>
      <div class="export-buttons">
        <button mat-button (click)="exportarExcel()"><mat-icon>table_chart</mat-icon> Excel</button>
        <button mat-button (click)="exportarPDF()"><mat-icon>picture_as_pdf</mat-icon> PDF</button>
      </div>
    </mat-card>

    <!-- Tabla de resultados -->
    <mat-table [dataSource]="datosReporte">
      <!-- columnas dinámicas según los datos -->
    </mat-table>
  </div>
</div>
```

## Paso 3 — Cómo probar

1. Ir a `/admin/reportes`
2. Escribir: *"cuántos trámites se completaron este mes"* → debe mostrar resultado con datos
3. Escribir algo ambiguo: *"dame un reporte de trámites"* → debe pedir: ¿de qué período?
4. Responder la pregunta → debe generar el reporte completo
5. Click en "Excel" → debe descargarse el archivo
6. Click en "PDF" → debe descargarse el PDF
7. Probar con el micrófono (Chrome)

---

---

# AGENTE 9 — Flutter: Offline-First + Documentos + Mejoras

## Paso 1 — Instalar dependencias (pubspec.yaml)

```yaml
dependencies:
  # Offline storage
  hive: ^2.2.3
  hive_flutter: ^1.1.0
  path_provider: ^2.1.2
  connectivity_plus: ^5.0.2
  
  # Documentos
  flutter_pdfview: ^1.3.2
  url_launcher: ^6.2.5
  file_picker: ^8.0.0
  
  # Ya existentes (verificar que estén)
  flutter_secure_storage: ^9.0.0
  socket_io_client: ^2.0.3+1
```

```bash
flutter pub get
```

## Paso 2 — Servicio de sincronización offline

**Archivo:** `lib/core/services/offline_sync_service.dart` *(crear)*

```dart
import 'package:hive_flutter/hive_flutter.dart';
import 'package:connectivity_plus/connectivity_plus.dart';

class OfflineSyncService {
  static const String _boxTareas = 'tareas_cache';
  static const String _boxFormularios = 'formularios_cache';
  static const String _boxPendientes = 'acciones_pendientes';

  /// Inicializar Hive al arrancar la app
  static Future<void> init() async {
    await Hive.initFlutter();
    await Hive.openBox(_boxTareas);
    await Hive.openBox(_boxFormularios);
    await Hive.openBox(_boxPendientes);
  }

  /// Guardar tareas del usuario en local (para modo offline)
  static Future<void> cachearTareas(List<Map<String, dynamic>> tareas) async {
    final box = Hive.box(_boxTareas);
    await box.put('mis_tareas', tareas);
    await box.put('ultima_sync', DateTime.now().toIso8601String());
  }

  /// Obtener tareas del cache
  static List<Map<String, dynamic>> obtenerTareasCacheadas() {
    final box = Hive.box(_boxTareas);
    final datos = box.get('mis_tareas');
    if (datos == null) return [];
    return List<Map<String, dynamic>>.from(datos);
  }

  /// Guardar acción pendiente (completar formulario sin internet)
  static Future<void> guardarAccionPendiente(Map<String, dynamic> accion) async {
    final box = Hive.box(_boxPendientes);
    final pendientes = List<Map<String, dynamic>>.from(box.get('lista') ?? []);
    pendientes.add({...accion, 'timestamp': DateTime.now().toIso8601String()});
    await box.put('lista', pendientes);
  }

  /// Sincronizar acciones pendientes cuando vuelve el internet
  static Future<void> sincronizarPendientes(Function(Map<String, dynamic>) ejecutar) async {
    final conectividad = await Connectivity().checkConnectivity();
    if (conectividad == ConnectivityResult.none) return;

    final box = Hive.box(_boxPendientes);
    final pendientes = List<Map<String, dynamic>>.from(box.get('lista') ?? []);

    for (final accion in pendientes) {
      try {
        await ejecutar(accion);
      } catch (e) {
        print('Error sincronizando acción: $e');
      }
    }
    await box.put('lista', []);
  }

  static bool get hayPendientes {
    final box = Hive.box(_boxPendientes);
    final pendientes = box.get('lista') ?? [];
    return pendientes.isNotEmpty;
  }
}
```

## Paso 3 — Modificar main.dart para offline

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await OfflineSyncService.init();   // ← agregar
  await NotificationService.init();
  runApp(const MyApp());
}
```

## Paso 4 — Modificar la pantalla de tareas para modo offline

```dart
// En mis_tareas_screen.dart — modificar cargarTareas()
Future<void> cargarTareas() async {
  final conectividad = await Connectivity().checkConnectivity();

  if (conectividad == ConnectivityResult.none) {
    // Sin internet: usar cache
    setState(() {
      tareas = OfflineSyncService.obtenerTareasCacheadas();
      modoOffline = true;
    });
    return;
  }

  // Con internet: cargar del backend y actualizar cache
  try {
    final tareasList = await tareaService.obtenerMisTareas();
    await OfflineSyncService.cachearTareas(tareasList);
    setState(() {
      tareas = tareasList;
      modoOffline = false;
    });

    // Sincronizar acciones pendientes
    await OfflineSyncService.sincronizarPendientes(_ejecutarAccionPendiente);
  } catch (e) {
    // Si falla, usar cache
    setState(() {
      tareas = OfflineSyncService.obtenerTareasCacheadas();
      modoOffline = true;
    });
  }
}
```

**Banner de modo offline:**
```dart
// Agregar en el scaffold de la app
if (modoOffline)
  MaterialBanner(
    backgroundColor: Colors.orange.shade100,
    content: Row(children: [
      Icon(Icons.wifi_off, color: Colors.orange),
      SizedBox(width: 8),
      Text('Modo offline — los cambios se sincronizarán al reconectarse'),
    ]),
    actions: [TextButton(onPressed: cargarTareas, child: Text('Reintentar'))],
  )
```

## Paso 5 — Pantalla de documentos en Flutter

**Archivo:** `lib/features/documentos/screens/documentos_screen.dart` *(crear)*

```dart
class DocumentosScreen extends StatefulWidget {
  final String tramiteId;
  const DocumentosScreen({required this.tramiteId, super.key});

  @override
  State<DocumentosScreen> createState() => _DocumentosScreenState();
}

class _DocumentosScreenState extends State<DocumentosScreen> {
  List<dynamic> documentos = [];
  bool cargando = true;

  @override
  void initState() {
    super.initState();
    _cargarDocumentos();
  }

  Future<void> _cargarDocumentos() async {
    final docs = await DocumentoService.listarPorTramite(widget.tramiteId);
    setState(() { documentos = docs; cargando = false; });
  }

  Future<void> _subirDocumento() async {
    final resultado = await FilePicker.platform.pickFiles(
      allowMultiple: false,
      type: FileType.any,
    );
    if (resultado == null) return;
    final archivo = resultado.files.first;
    await DocumentoService.subirDocumento(archivo, widget.tramiteId);
    _cargarDocumentos();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Documentos del Trámite')),
      body: cargando
          ? const Center(child: CircularProgressIndicator())
          : ListView.builder(
              itemCount: documentos.length,
              itemBuilder: (ctx, i) {
                final doc = documentos[i];
                return ListTile(
                  leading: _iconoPorTipo(doc['tipoMime']),
                  title: Text(doc['nombre']),
                  subtitle: Text('v${doc['version']} · ${doc['creadoPorNombre']}'),
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    IconButton(
                      icon: const Icon(Icons.visibility),
                      onPressed: () => _verDocumento(doc),
                    ),
                    IconButton(
                      icon: const Icon(Icons.download),
                      onPressed: () => _descargar(doc),
                    ),
                  ]),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: _subirDocumento,
        child: const Icon(Icons.upload_file),
      ),
    );
  }

  void _verDocumento(Map<String, dynamic> doc) {
    final mime = doc['tipoMime'] as String? ?? '';
    if (mime.contains('pdf')) {
      Navigator.push(context, MaterialPageRoute(
        builder: (_) => PDFViewerScreen(url: doc['urlArchivo'], titulo: doc['nombre']),
      ));
    } else if (mime.startsWith('image/')) {
      Navigator.push(context, MaterialPageRoute(
        builder: (_) => ImageViewerScreen(url: doc['urlArchivo']),
      ));
    } else {
      launchUrl(Uri.parse(doc['urlArchivo']));
    }
  }
}
```

## Paso 6 — Cómo probar el modo offline

1. Abrir la app con internet → cargar las tareas → se guardan en Hive
2. Desactivar el WiFi/datos del emulador
3. Cerrar y reabrir la app → debe mostrar las tareas del cache + banner naranja de offline
4. Completar un formulario → debe guardarse como acción pendiente localmente
5. Reactivar el WiFi → debe sincronizarse automáticamente al reconectarse
6. Verificar en Swagger que el formulario completado offline llegó al backend

---

---

## Checklist Final — Verificación antes del examen

### Backend (wm_backend)
- [ ] `DiagramaWebSocketController.java` implementado — diagrama colaborativo en tiempo real
- [ ] `AwsS3Config.java` y `S3Service.java` implementados
- [ ] Variables AWS en `.env` configuradas
- [ ] `Documento.java`, `AuditoriaDocumento.java` creados
- [ ] `DocumentoController.java` con todos los endpoints funcionando
- [ ] `OnlyOfficeCallbackController.java` implementado
- [ ] `ReporteService.java` con consultas de agregación MongoDB
- [ ] `ReporteController.java` con exportación Excel/PDF

### Microservicio IA (wm_ai)
- [ ] Migrado de Django a **FastAPI** — `GET /health` responde `"framework": "FastAPI"`
- [ ] `python train_model.py` ejecuta sin errores y genera `modelo_cuello_botella.h5`
- [ ] `POST /ia/generar-analisis` usa el modelo TensorFlow (no RandomForest)
- [ ] `POST /ia/generar-reporte` interpreta consultas en español correctamente
- [ ] `sentence-transformers` instalado y funcional

### Frontend (wm_frontend)
- [ ] Módulo de documentos en `/admin/documentos` — subir, ver, versiones, auditoría
- [ ] OnlyOffice embebido — edición colaborativa funcional con Docker
- [ ] Dos admins editando el mismo diagrama → cambios en tiempo real ✅
- [ ] Reportes dinámicos por voz/texto → exportación Excel y PDF
- [ ] App configurada como PWA — Service Worker registrado en DevTools
- [ ] Modo offline en PWA — carga con cache cuando no hay internet

### App móvil (wm_mobile)
- [ ] Hive inicializado en `main.dart`
- [ ] Pantalla de tareas funciona offline con cache
- [ ] Acciones pendientes se sincronizan al volver internet
- [ ] Banner naranja visible cuando está offline
- [ ] Módulo de documentos — listar, subir, ver PDF/imagen
- [ ] APK generado: `flutter build apk --release`

### Infraestructura
- [ ] OnlyOffice corriendo en Docker: `docker ps` muestra `wm-onlyoffice`
- [ ] AWS S3 bucket creado y accesible
- [ ] `modelo_cuello_botella.h5` commiteado en el repo o generado al iniciar

---

## Orden de implementación recomendado (2 días antes del viernes)

```
DÍA 1 (hoy):
  Mañana:   Agente 0 — Diagrama colaborativo (es el que más puntos perdiste)
  Tarde:    Agente 1 — Migración FastAPI
  Noche:    Agente 2 — Modelo TensorFlow

DÍA 2 (mañana):
  Mañana:   Agente 3 — Reportes NLP + Agente 4 — AWS S3
  Tarde:    Agente 5 — API Documental + Agente 6 — OnlyOffice Docker
  Noche:    Agente 7 — Angular PWA + Agente 8 — Reportes frontend

DÍA 3 (jueves):
  Mañana:   Agente 9 — Flutter offline
  Tarde:    Testing end-to-end + APK
  Noche:    Pulido + commits limpios

VIERNES:
  Recibir confirmación del agente chatbot → Agente 10 aparte
```

---

## Notas de uso para el agente en Visual Studio

Cuando uses estas guías con tu agente de Claude en VS Code, **pásale un agente a la vez**. El prompt de inicio para cada agente es:

```
Eres un agente de desarrollo para el proyecto WorkflowManager (2do Parcial Ingeniería de Software I).
Stack: Spring Boot (wm_backend), Angular (wm_frontend), Flutter (wm_mobile), FastAPI Python (wm_ai).
Implementa exactamente lo que dice la guía del [AGENTE X — nombre].
No modifiques código que no está en el alcance del agente.
Al terminar, lista los archivos creados/modificados y cómo probarlo.
```

Pega el contenido completo del agente correspondiente después de ese prompt.
