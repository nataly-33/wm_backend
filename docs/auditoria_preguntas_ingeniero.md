# AUDITORÍA Y CORRECCIÓN — 3 Preguntas del Ingeniero
**Repos:** `wm_backend` · `wm_frontend` · `wm_mobile` · `wm_ai`  
**Instrucción general:** Antes de implementar cualquier cosa, LEER los archivos indicados y verificar qué existe y qué no. Reportar el estado de cada punto antes de tocar código.

---

## PREGUNTA 1 — ¿El Deep Learning funciona sin conexión a internet?

### Qué leer primero

**En `wm_ai`:**
- `requirements.txt` — ¿está `tensorflow` o `keras`?
- `app/services/analisis_service.py` — ¿carga un modelo local (`.h5`, `.pkl`) o llama a API externa (Groq, OpenAI)?
- Buscar si existe `modelo_cuello_botella.h5` o `.pkl` en la raíz
- `train_model.py` si existe

**En `app/services/reporte_service.py`:**
- ¿Usa `sentence-transformers` con modelo descargado localmente o llama a una URL externa?
- Buscar cualquier `requests.post` a dominios externos en los servicios de IA

### Estados posibles

**Caso A — usa TensorFlow/Keras con modelo `.h5` local:** ✅ cumple. No tocar.

**Caso B — usa scikit-learn (RandomForest, GradientBoosting):** ❌ no es Deep Learning. Implementar red neuronal con TensorFlow según la guía `correccion_agente2_deep_learning_tensorflow.md`. El modelo se entrena localmente con `python train_model.py`, se guarda como `.h5` y no necesita internet para inferencia.

**Caso C — llama a Groq u otra API para el análisis de cuellos de botella:** ❌ crítico. Reemplazar con modelo local. Groq puede seguir usándose SOLO para reportes en lenguaje natural (eso es NLP conversacional, distinto del Deep Learning del análisis).

### Lo que debe quedar
- `analisis_service.py` carga el `.h5` al iniciar el servidor, sin llamadas externas
- `POST /ia/generar-analisis` debe responder con el servidor sin internet
- El `.h5` debe commitearse o generarse con `train_model.py` al hacer deploy

---

## PREGUNTA 2 — ¿Los formularios dinámicos incluyen checklist, selectores, grid/tabla?

### Qué leer primero

**En `wm_backend`:**
- El enum `TipoCampo.java` — listar TODOS los valores actuales
- `CampoFormulario.java` — ver qué campos tiene el modelo

**En `wm_frontend`:**
- El componente donde el ADMIN DISEÑA el formulario (buscar en `editor-diagrama` o `formulario-editor`) — verificar que el dropdown de "tipo de campo" muestra TODOS los tipos
- El componente donde el FUNCIONARIO/CLIENTE RELLENA el formulario — verificar que hay un `case` o `*ngIf` para cada tipo:
  - Texto corto → `<input>`
  - Área de texto → `<textarea>`
  - Número → `<input type="number">`
  - Fecha → datepicker
  - Selector/desplegable → `<mat-select>` con opciones ← verificar
  - Radio → `<mat-radio-group>` ← verificar si existe
  - Checkbox/casilla → `<mat-checkbox>` múltiple ← verificar si existe
  - Archivo → `<input type="file">`
  - Imagen → `<input type="file">` con preview
  - Tabla/Grid → tabla con filas dinámicas ← el que colapsó en el 1er parcial, verificar con cuidado

**En `wm_mobile`:**
- Buscar el widget que renderiza campos en Flutter — verificar los mismos tipos

### Qué implementar si falta

**Si falta CHECKBOX:** renderizar lista de `mat-checkbox` por cada opción en `campo.opciones`, guardar array de strings seleccionados.

**Si falta RADIO:** `mat-radio-group` con `mat-radio-button` por cada opción en `campo.opciones`.

**Si SELECTOR no funciona:** verificar que `mat-select` recibe `campo.opciones` y las renderiza.

**Si TABLA_GRID no existe o crashea — CRÍTICO:**
- Implementar tabla Angular Material con columnas de `campo.columnasGrid` (o `campo.opciones`)
- Botón "Agregar fila" → agrega objeto vacío al array
- Cada celda es un `<input>` editable
- Botón eliminar fila
- Valor guardado: `Array<{col: valor}>`
- Verificar que no hay `undefined` al inicializar — la causa más común del crash

**Si el enum no tiene todos los tipos:** agregar los faltantes en `TipoCampo.java` Y en el dropdown del editor de formulario.

### Verificación
- Crear formulario con un campo de cada tipo, asignarlo a un nodo, rellenarlo
- Todos deben renderizar sin errores de consola
- Verificar en MongoDB que el valor guardado tiene el tipo correcto (string, number, array, array-of-objects)

---

## PREGUNTA 3 — ¿Gestión documental completa con control de versiones, colaborativo multiusuario y S3?

### 3a — AWS S3

**Qué leer:**
- `wm_backend/src/.../config/AwsS3Config.java` — ¿existe? ¿inicializa el cliente S3 con las credenciales?
- `wm_backend/src/.../archivo/service/S3Service.java` o `DocumentoService.java` — ¿llama a AWS SDK para subir? ¿o guarda en disco local?
- `application.properties` o `.env` — ¿están `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET`?

**Si S3 no está conectado:** configurar credenciales reales. El bucket debe existir en AWS. Verificar que `S3Service.subirArchivo()` retorna una URL pública de S3 (no una ruta local).

**Verificación:** subir un archivo desde la UI → debe aparecer en el bucket S3 con estructura `{empresaId}/{clienteId}/{tramiteId}/{departamento}/`.

---

### 3b — Control de versiones

**Qué leer:**
- `Documento.java` — ¿tiene campo `version` (Integer) y `historialVersiones` (List)?
- `DocumentoService.java` — ¿existe método `subirNuevaVersion()`? Debe: incrementar `version`, agregar la URL anterior al historial, subir el nuevo archivo a S3, actualizar `urlArchivo`
- ¿Existe `GET /api/v1/documentos/{id}/versiones`?
- ¿Existe `POST /api/v1/documentos/{id}/version`?

**En el frontend — qué buscar:**
- En el módulo de documentos (`/admin/documentos`), ¿hay una sección o botón de "versiones" o "historial"?
- ¿Se puede ver la lista de versiones anteriores con su fecha, autor y link de descarga?
- ¿Hay botón "Subir nueva versión"?

**Si no existe la vista de versiones en el frontend:** crear en la vista de detalle del documento una sección colapsable que muestre tabla con: número de versión, fecha, quién subió, botón descargar. Botón "Nueva versión" que abre file picker y llama al endpoint.

---

### 3c — Edición colaborativa multiusuario (OnlyOffice)

**Qué verificar:**
```bash
docker ps | grep onlyoffice
# Si no corre: docker-compose up -d onlyoffice
```

**En `wm_backend`:**
- ¿Existe `OnlyOfficeCallbackController.java` con `POST /api/v1/onlyoffice/callback/{documentoId}`?
- ¿Existe en `DocumentoService.java` un método que genera la config JSON para el editor (con `document.url`, `document.key`, `editorConfig.callbackUrl`, `editorConfig.user`)?
- ¿Existe `GET /api/v1/documentos/{id}/config-editor`?

**En `wm_frontend`:**
- ¿Existe componente `editor-onlyoffice` o similar?
- ¿Carga el script desde `http://localhost:8088/web-apps/apps/api/documents/api.js`?
- ¿Usa la config del backend para inicializar `new DocsAPI.DocEditor(...)`?

**Si OnlyOffice no está dockerizado:** crear `docker-compose.yml` con el servicio `onlyoffice/documentserver`, puerto 8088, `JWT_ENABLED=true`.

**Si el componente Angular no existe:** crearlo — cargar el script de OnlyOffice dinámicamente, recibir la config del backend, inicializar el editor en un div.

**Prueba de colaboración:**
1. Subir un `.docx` → abrir en editor → debe verse OnlyOffice embebido
2. Abrir el mismo doc en otro browser con otro usuario → ambos ven cambios en tiempo real
3. Al cerrar → el callback guarda nueva versión en S3

---

## Reporte que el agente debe generar ANTES de tocar código

```
PREGUNTA 1 — Deep Learning sin internet:
[ ] tensorflow en requirements.txt: SÍ / NO
[ ] modelo_cuello_botella.h5 existe: SÍ / NO
[ ] analisis_service.py usa: LOCAL / GROQ API / SKLEARN
[ ] Funciona sin internet: SÍ / NO / PARCIAL
[ ] Qué falta implementar: ...

PREGUNTA 2 — Tipos de campo:
[ ] Valores actuales del enum TipoCampo: [listar]
[ ] SELECTOR funciona en editor y en relleno: SÍ / NO
[ ] CHECKBOX existe en editor y relleno: SÍ / NO
[ ] RADIO existe en editor y relleno: SÍ / NO
[ ] TABLA_GRID existe y no crashea: SÍ / NO
[ ] Flutter tiene los mismos tipos: SÍ / NO
[ ] Qué falta implementar: ...

PREGUNTA 3 — Gestión documental:
[ ] S3Service llama a AWS SDK (no guarda en disco): SÍ / NO
[ ] Variables AWS configuradas: SÍ / NO
[ ] Documento.java tiene historialVersiones: SÍ / NO
[ ] Endpoint subir nueva versión existe: SÍ / NO
[ ] Vista de historial de versiones en frontend: SÍ / NO
[ ] OnlyOffice dockerizado y corriendo: SÍ / NO
[ ] Componente editor-onlyoffice en Angular: SÍ / NO
[ ] Callback OnlyOffice guarda en S3: SÍ / NO
[ ] Qué falta implementar: ...
```

Solo después de completar ese reporte, implementar lo que falte según las guías ya entregadas (`correccion_agente2`, `correccion_agente4`, `correccion_agente5`, `agente_6_onlyoffice`).
