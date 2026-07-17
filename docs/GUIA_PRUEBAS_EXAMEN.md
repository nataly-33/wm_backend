# GUÍA DE PRUEBAS — EXAMEN WorkflowManager CRE
**URL del sistema:** `http://100.49.195.187` (Frontend) | `http://100.49.195.187:8080` (Backend) | `http://100.49.195.187:8088` (OnlyOffice)

> La demo es en el entorno desplegado en AWS EC2. El ingeniero accede desde su browser a la IP pública. Solo se muestra localhost si el inge pide ver/modificar código.

---

## ANTES DEL EXAMEN — Verificar que todo está corriendo en EC2

Conectarte por SSH desde PowerShell (desde la carpeta donde está tu .pem):
```powershell
ssh -i "workflow-key.pem" ubuntu@100.49.195.187
```

Una vez dentro de EC2:
```bash
# Ver estado de todos los contenedores
docker ps

# Deben aparecer estos 4 contenedores en estado "Up":
# wm-frontend    Up   0.0.0.0:80->80/tcp
# wm-backend     Up   0.0.0.0:8080->8080/tcp
# wm-ai          Up   0.0.0.0:8001->8001/tcp
# wm-onlyoffice  Up   0.0.0.0:8088->80/tcp
```

Si alguno no está corriendo:
```bash
cd /home/ubuntu
docker compose -f docker-compose.prod.yml up -d

# Esperar 2 minutos y verificar de nuevo
docker ps
```

**Verificación rápida desde el browser (antes que llegue el inge):**
- `http://100.49.195.187` → debe mostrar la pantalla de login del sistema
- `http://100.49.195.187:8080/api/v1/auth/health` → `{"status":"UP"}`
- `http://100.49.195.187:8088` → pantalla de bienvenida de OnlyOffice

### Si EC2 no responde (SSH timeout)
1. Ir a [console.aws.amazon.com](https://console.aws.amazon.com) → EC2 → Instances
2. Seleccionar la instancia → Actions → **Instance State → Reboot** (NO "Stop" — cambia la IP)
3. Esperar 2 minutos → intentar SSH de nuevo

---

## PREGUNTA 1 — Deep Learning sin internet

### Lo que significa "sin internet" en este contexto

**NO significa desconectar el servidor.** Si desconectas EC2 de internet, nadie puede acceder a la app. Significa que el modelo de IA **no llama a OpenAI, Groq, ni ninguna API externa** para hacer el análisis. Todo corre localmente en el servidor EC2.

La prueba es:
1. Mostrar el código `analisis_service.py` → no hay ningún `requests.post` a servicios externos
2. El modelo está guardado en el contenedor Docker → no necesita descargar nada
3. Ejecutar el análisis → ver en los logs que solo hay tráfico interno (EC2 → MongoDB Atlas)

### Cómo funciona (para defender ante el inge)

```
Browser del inge ──GET http://100.49.195.187──▶ wm-frontend (Nginx, puerto 80)
                                               │
Browser ──POST http://100.49.195.187:8080──▶ wm-backend (Spring Boot, puerto 8080)
                                               │
                                               └──POST http://wm-ai:8001──▶ wm-ai (FastAPI)
                                                                              │
                                                                              ├─ Carga modelo_cuello_botella.h5 (está dentro del contenedor)
                                                                              ├─ Normaliza datos con scaler_cuello_botella.pkl
                                                                              ├─ Red neuronal predice: BAJO / MEDIO / ALTO / CRITICO
                                                                              └─ Devuelve JSON de resultados
```

**Los 3 archivos del modelo:**

| Archivo | Qué es | Cuándo se generó |
|---|---|---|
| `modelo_cuello_botella.h5` | Red neuronal TensorFlow/Keras entrenada | Se ejecutó `train_model.py` al hacer `docker build` |
| `scaler_cuello_botella.pkl` | Normalizador de datos (StandardScaler) | Mismo momento — necesario para que los números estén en la escala correcta |
| `modelo_metadata.json` | Versión y metadatos del modelo | Mismo momento |

**Sobre los datos de entrenamiento:** El modelo se entrenó con **5,000 muestras sintéticas** generadas con distribuciones estadísticas reales (tiempos exponenciales, tasas beta, cargas uniformes). No necesitó datos reales de trámites para entrenarse.

**Sobre los datos de inferencia:** Para PREDECIR cuellos de botella, usa los trámites REALES del seeder CRE que están en MongoDB (EjecucionNodo). Cuantos más trámites reales, mejor el análisis.

### Prueba en EC2

1. Abrir `http://100.49.195.187` → Login como Admin (admin@cre.com.bo / password del seeder)
2. Navegar a **Dashboard → Análisis IA** o **Reportes**
3. Hacer clic en **"Generar Análisis"**
4. Debe mostrar lista de nodos con barras de severidad coloreadas

**Para demostrar que es local (mostrar mientras el inge mira):**
```bash
# En EC2 por SSH, ver los logs del contenedor IA mientras se ejecuta el análisis
docker logs wm-ai --tail=20 -f
# Se verá algo como:
# INFO: Modelo TensorFlow cargado (version v4_tensorflow)
# INFO: Predicción completada para 8 nodos
# NO debe aparecer ninguna URL de openai.com, groq.com, etc.
```

**Frase para el inge:**
> "El análisis de cuellos de botella usa una red neuronal TensorFlow con 3 capas densas, entrenada localmente con datos sintéticos. El modelo pesa 150KB y está dentro del contenedor Docker. Cuando el backend Java envía las métricas al microservicio Python, este carga el `.h5`, normaliza los datos y predice la severidad. No hay ninguna llamada a API externa — puedo mostrar los logs del contenedor en tiempo real."

---

## PREGUNTA 2 — Formularios dinámicos con TODOS los tipos de campo

### Los 11 tipos de campo

```
TEXTO_CORTO | AREA_TEXTO | ETIQUETA | NUMERO | FECHA |
SELECTOR | RADIO | CHECKBOX | ARCHIVO | IMAGEN | TABLA_GRID
```

### Prueba 2A — Ver el diseñador de formularios

1. `http://100.49.195.187` → Login como Admin
2. **Políticas** → Seleccionar "Solicitud de Reconexión de Servicio" (o cualquier política)
3. **Editor de Diagrama** → clic en un nodo → **"Editar Formulario"**
4. **"Agregar campo"** → el dropdown **"Tipo"** debe mostrar los 11 tipos

### Prueba 2B — Rellenar formulario como Funcionario

1. Login como Funcionario (ej: funcionario del depto Técnico)
2. **Mis Tareas** → abrir una tarea activa
3. Verificar que cada campo se renderiza correctamente:

| Tipo | Cómo se ve en el formulario web |
|---|---|
| TEXTO_CORTO | Input de una línea |
| AREA_TEXTO | Caja de texto multilinea |
| ETIQUETA | Texto estático informativo (sin input) |
| NUMERO | Input numérico |
| FECHA | Calendario al hacer clic |
| SELECTOR | Dropdown con opciones configuradas |
| RADIO | Botones de opción (uno solo seleccionable) |
| CHECKBOX | Casillas independientes (múltiples seleccionables) |
| ARCHIVO | Botón subir archivo → abre explorador |
| IMAGEN | Botón subir imagen → muestra preview |
| TABLA_GRID | Tabla vacía + botón "Agregar fila" → inputs por columna |

4. Completar y guardar el formulario

**Si el inge pide ver en MongoDB Compass:**
- Colección `ejecuciones_nodo` → campo `respuestas`
- CHECKBOX: `["Opción 1", "Opción 2"]` (array de strings)
- TABLA_GRID: `[{"Columna1":"valor","Columna2":"valor"}]` (array de objetos)
- SELECTOR: `"Opción seleccionada"` (string)

### Prueba 2C — App móvil Flutter

1. Abrir la app → Login como Cliente
2. **"Iniciar trámite"** → chatbot → seguir el flujo
3. Los campos se muestran uno a uno como widgets Flutter:
   - SELECTOR → `DropdownButtonFormField`
   - RADIO → `RadioListTile` (botones circulares)
   - CHECKBOX → `CheckboxListTile` (casillas)
   - TABLA_GRID → tabla con "Agregar fila"
   - FECHA → DatePicker nativo

### TABLA_GRID — la más crítica (colapsó en el 1er parcial)

**Verificar específicamente:**
1. Abrir formulario con campo TABLA_GRID
2. **Debe aparecer la tabla vacía SIN error** (no "undefined")
3. Clic "Agregar fila" → aparece una fila con inputs vacíos para cada columna
4. Llenar → completar tarea → en MongoDB debe verse array de objetos

---

## PREGUNTA 3 — Gestión documental completa con S3, versiones y OnlyOffice

### 3A — Almacenamiento en AWS S3

**Prueba:**
1. Login como Cliente (app móvil o web) → iniciar trámite
2. En un campo ARCHIVO → subir cualquier PDF o imagen
3. Completar el trámite
4. En MongoDB Compass → colección `ejecuciones_nodo` → `respuestas` → campo de tipo ARCHIVO
5. La URL debe ser: `https://wm-documentos.s3.amazonaws.com/{clienteId}/{tramiteId}/{deptoId}/{timestamp}_{nombre}`

**Frase para el inge:**
> "Todo archivo pasa por `S3Service.subirArchivo()` que usa el AWS SDK v2 (`software.amazon.awssdk`). Si las credenciales están configuradas, sube directamente al bucket S3 de AWS. La estructura de carpetas organiza por clienteId, tramiteId y departamento para recuperación fácil."

**Si la URL empieza con `https://s3-simulado/`:** Las credenciales AWS no están activas. Mostrar el código de `S3Service.java` que demuestra la integración con AWS SDK.

### 3B — Control de versiones de documentos

**Para documentos del panel de Admin (documentos de oficina y subidos):**

1. Login como Admin → `http://100.49.195.187` → **Documentos**
2. La tabla muestra columna **Versión** con `v1` para cada documento
3. Hacer clic en el botón **upload** (ícono subir, fila del documento)
4. Subir una versión nueva del archivo
5. La versión pasa a `v2`
6. Hacer clic en el botón **history** (ícono historial)
7. Modal "Versiones" muestra:
   - v1: quién subió, fecha, tamaño, botón descargar
   - v2: quién subió, fecha, tamaño, botón descargar
8. **Descargar v1** → debe descargarse el archivo anterior

**Para documentos de trámite editados con OnlyOffice:**

1. Admin → **Historial de trámite** de un trámite que tenga archivos Word/Excel
2. Junto al archivo aparece botón **"Editar"** (azul) → clic
3. Se abre OnlyOffice en el editor
4. Hacer algún cambio → cerrar la pestaña
5. OnlyOffice envía callback automático al backend
6. Volver al historial → junto al link del archivo aparece badge amarillo **`v2`**

**Frase para el inge:**
> "El modelo `Documento` tiene `Integer version` e `List<VersionDocumento> historialVersiones`. Cada nueva versión guarda la URL anterior en el historial con fecha, autor y tamaño, y sube el nuevo archivo a S3 con una key nueva. Para documentos de trámite editados en OnlyOffice, el callback también registra versiones en el modelo `DocumentoTramite`."

### 3C — Edición colaborativa con OnlyOffice

**Verificar que OnlyOffice está corriendo:**
```bash
# En EC2
docker ps | grep onlyoffice
# Debe mostrar: wm-onlyoffice ... Up ... 0.0.0.0:8088->80/tcp
```

Abrir `http://100.49.195.187:8088` → debe mostrar pantalla de OnlyOffice Document Server.

**Prueba del editor:**
1. Admin → **Documentos** → Sección "Documentos de oficina"
2. **Crear** → elegir Word (docx) → poner nombre → "Crear y abrir"
3. Se abre el editor OnlyOffice embebido en la misma página
4. Escribir algo en el documento

**Prueba de colaboración multiusuario (la prueba estrella):**
1. Abrir `http://100.49.195.187` en **Chrome** → Login como Admin → abrir el mismo documento
2. Abrir `http://100.49.195.187` en **Firefox** → Login como otro usuario → abrir el mismo documento
3. Escribir texto en Chrome → debe aparecer en Firefox en tiempo real
4. Cada usuario tiene un cursor de color diferente con su nombre

**Frase para el inge:**
> "OnlyOffice Document Server corre en Docker en el puerto 8088 del mismo servidor EC2. El backend Spring Boot genera una config JSON con JWT firmado, la URL del documento en S3, y la URL de callback. El componente Angular `EditorOnlyofficeComponent` carga el script de OnlyOffice dinámicamente con `DocsAPI.DocEditor`. Cuando se guarda, el callback `POST /api/v1/onlyoffice/callback/{id}` descarga el nuevo contenido y lo guarda en S3, registrando una nueva versión."

---

## CHECKLIST DEL DÍA DEL EXAMEN

### 30 minutos antes

```bash
# Conectar por SSH
ssh -i "workflow-key.pem" ubuntu@100.49.195.187

# Verificar los 4 contenedores
docker ps

# Si alguno está caído:
cd /home/ubuntu && docker compose -f docker-compose.prod.yml up -d

# Ver logs del backend para confirmar que arrancó bien
docker logs wm-backend --tail=20
# Debe decir: "Started WorkflowBackApplication in X.X seconds"

# Ver logs de IA
docker logs wm-ai --tail=10
# Debe decir: "Application startup complete"
```

### Verificar en el browser antes

- [ ] `http://100.49.195.187` → pantalla de login (no blank page)
- [ ] Login con admin@cre.com.bo → entra al dashboard
- [ ] `http://100.49.195.187:8080/api/v1/auth/health` → `{"status":"UP"}`
- [ ] `http://100.49.195.187:8088` → pantalla OnlyOffice

### PREGUNTA 1 — Checklist

- [ ] Dashboard → Análisis IA → clic "Generar Análisis" → muestra resultados
- [ ] Los resultados tienen nodos con severidad BAJO/MEDIO/ALTO/CRITICO
- [ ] Abrir terminal EC2 → `docker logs wm-ai -f` → ejecutar análisis → ver que no hay URLs de openai.com o groq.com en los logs
- [ ] Mostrar `analisis_service.py` línea 25: `keras.models.load_model('modelo_cuello_botella.h5')` — modelo local

### PREGUNTA 2 — Checklist

- [ ] Editor de diagrama → agregar campo → dropdown muestra 11 tipos
- [ ] Formulario funcionario web → SELECTOR muestra dropdown con opciones
- [ ] Formulario funcionario web → CHECKBOX muestra casillas independientes
- [ ] Formulario funcionario web → RADIO muestra botones de opción
- [ ] Formulario funcionario web → TABLA_GRID → "Agregar fila" funciona
- [ ] App móvil → SELECTOR, RADIO, CHECKBOX, TABLA_GRID funcionan
- [ ] Completar formulario → MongoDB Compass → CHECKBOX es array, TABLA_GRID es array de objetos

### PREGUNTA 3 — Checklist

- [ ] Subir archivo en trámite → URL en MongoDB empieza con `https://wm-documentos.s3.amazonaws.com/`
- [ ] Panel Documentos → columna Versión muestra `v1`
- [ ] Botón upload en fila → subir nueva versión → se actualiza a `v2`
- [ ] Botón history → modal muestra versiones anteriores con descarga
- [ ] Editar doc trámite en OnlyOffice → badge amarillo `v2` en historial
- [ ] `http://100.49.195.187:8088` accesible → editor carga con barra herramientas
- [ ] Mismo doc en Chrome y Firefox → cambios sincronizan en tiempo real

---

## FRASES EXACTAS PARA LA DEFENSA

**PREGUNTA 1:**
> "El análisis de cuellos de botella usa una red neuronal TensorFlow con 3 capas densas. El modelo se entrena con `train_model.py` usando 5,000 muestras sintéticas con distribuciones estadísticas reales. El modelo entrenado se guarda como `modelo_cuello_botella.h5` dentro del contenedor Docker. Cuando el admin solicita un análisis, el backend Java envía las métricas de los nodos al microservicio FastAPI, que carga el `.h5` localmente y predice la severidad. No hay ninguna llamada a OpenAI, Groq ni ninguna API externa — todo corre en el servidor EC2."

**PREGUNTA 2:**
> "El sistema tiene 11 tipos de campo definidos en el enum `TipoCampo`. En Angular, el componente `ejecutar-tarea` tiene un `ngSwitch` con un case para cada tipo. En Flutter, `ejecutar_tarea_screen.dart` tiene el mismo switch en Dart. Los tipos más interesantes son: SELECTOR que guarda un string, CHECKBOX que guarda un array de strings, RADIO que guarda un string único, y TABLA_GRID que guarda un array de objetos JSON donde cada objeto es una fila con los campos como claves."

**PREGUNTA 3:**
> "La gestión documental tiene tres pilares. S3: todo archivo usa `S3Service.subirArchivo()` con el AWS SDK v2, con estructura `{clienteId}/{tramiteId}/{deptoId}/{timestamp}_{nombre}`. Versiones: el modelo `Documento` tiene `Integer version` y `List<VersionDocumento> historialVersiones` — cada versión nueva sube a S3 y la URL anterior queda en el historial recuperable. OnlyOffice: corre en Docker, el backend genera config JWT firmada, Angular carga el editor con `DocsAPI.DocEditor`, y al guardar el callback actualiza S3 y registra nueva versión. La colaboración funciona porque OnlyOffice maneja la sincronización en tiempo real entre múltiples sesiones del mismo documento."

---

## SOLUCIÓN DE PROBLEMAS FRECUENTES EN EC2

| Síntoma | Causa probable | Solución |
|---|---|---|
| SSH timeout | EC2 sin RAM — contenedores saturaron la memoria | AWS Console → Reboot instance (no Stop) |
| Login dice "ERR_CONNECTION_REFUSED" | wm-backend caído | `docker start wm-backend` |
| Frontend en blanco | wm-frontend caído | `docker start wm-frontend` |
| Análisis IA falla con 500 | wm-ai caído | `docker start wm-ai` |
| Editor OnlyOffice no carga | wm-onlyoffice tarda 60s en iniciar | Esperar 1 minuto, recargar página |
| `http://100.49.195.187` no abre | wm-frontend caído o puerto 80 bloqueado | Verificar Security Group tiene puerto 80 abierto |
| MongoDB error en logs | `MONGODB_URI` mal configurado | `nano /home/ubuntu/.env` → verificar la URI de Atlas |
