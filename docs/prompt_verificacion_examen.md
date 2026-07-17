# PROMPT — Verificación y completado de los 5 criterios del examen parcial
## WorkflowManager · Para Roo Code · URGENTE

---

## CONTEXTO
Sistema WorkflowManager. Stack: Spring Boot + MongoDB + Angular 17 + JointJS + FastAPI.
Mañana es el examen parcial con el Ing. Martínez. Hay 5 criterios de evaluación de 20pts cada uno.
Este prompt verifica y completa TODO lo que falta para maximizar la nota.
Lee cada sección completa antes de tocar cualquier archivo.

---

## CRITERIO 1 (20pts) — La aplicación está terminada y funcionando al 100%

### Verificar que estos flujos funcionan sin errores:

**Flujo Admin General:**
```
Login → Dashboard → Departamentos (CRUD) → Usuarios (CRUD con roles)
→ Políticas (lista) → crear política → editor diagrama → guardar → activar
→ Iniciar trámite → Monitor en tiempo real → Análisis IA
```

**Flujo Admin Departamento:**
```
Login → Ver formularios de su departamento → crear/editar formulario
→ Ver trámites activos en su área
```

**Flujo Funcionario (web):**
```
Login → Mis Tareas → abrir tarea → ver formulario dinámico
→ completar con datos → trámite avanza al siguiente departamento
```

**Flujo Funcionario (Flutter):**
```
Login APK → lista de tareas → abrir tarea → formulario → completar
→ monitor web se actualiza sin recargar
```

### Corregir si falta:
- El header debe mostrar el nombre del usuario y su rol en TODOS los módulos
  (admin general, admin departamento, funcionario)
- Sidebar correcto por rol:
  - Admin General: Dashboard, Departamentos, Usuarios, Políticas, Formularios, Trámites, Monitor, Análisis IA
  - Admin Departamento: Dashboard, Mis Formularios, Mis Trámites
  - Funcionario: Mis Tareas
- Todos los CRUD deben tener confirmación antes de eliminar
- Los errores HTTP deben mostrar mensajes legibles (no "Http failure response")

---

## CRITERIO 2 (20pts) — Diagrama UML 2.5 con carriles y notación correcta

### Lo que Martinez verifica:
- Los carriles son VERTICALES (columnas), no filas horizontales
- Cada carril tiene el nombre del departamento en la parte SUPERIOR
- Las formas UML 2.5 son CORRECTAS:
  - INICIO: círculo negro sólido ⬤
  - TAREA/ACTIVIDAD: rectángulo con esquinas redondeadas, sin relleno de color
  - DECISIÓN: rombo ◆ sin relleno
  - FORK/JOIN: barra negra gruesa horizontal ▬
  - FIN: círculo con punto interior (doble círculo) ⦿
- Las flechas conectan nodos correctamente (control flow)
- Las etiquetas en transiciones de DECISIÓN tienen corchetes: [Aprobado] [Rechazado]
- El diagrama se guarda y recarga correctamente (misma disposición visual)
- Se puede exportar como PNG y PDF

### Verificar que funciona:
```
Abrir política "Instalación de Nuevo Medidor" → el diagrama carga con nodos organizados
Agregar un nodo TAREA a un carril → se ve correcto visualmente
Conectar dos nodos con flecha → la flecha aparece con punta correcta
Guardar → cerrar → reabrir → diagrama idéntico
Exportar PNG → la imagen incluye los carriles y nodos correctamente
```

### Corregir si falta:
Si los carriles son horizontales o las formas no tienen el estilo UML correcto,
revisar en:
```
wm_frontend/src/app/modules/admin/pages/politicas/editor-diagrama/
  - diagram-canvas.component.ts   → configuración de JointJS
  - uml-shapes.constants.ts       → estilos de cada forma UML
  - editor-diagrama.component.scss → CSS del canvas
```

---

## CRITERIO 3 (20pts) — Formularios dinámicos con componentes: etiquetas, textarea, botones, grids

### LO MÁS IMPORTANTE DEL EXAMEN — Martinez evaluó esto con NO al otro grupo

Martinez espera ver estos componentes en el diseñador de formularios:
- **Etiqueta (Label)** — texto descriptivo sin campo de entrada
- **Texto corto** — input text de una línea
- **Textarea** — campo de texto de múltiples líneas (esto es lo que faltó al otro grupo)
- **Botones de opción (Radio buttons)** — selección única entre opciones
- **Casillas (Checkboxes)** — selección múltiple
- **Selector desplegable (Select/Dropdown)** — lista de opciones
- **Número** — input numérico
- **Fecha** — date picker
- **Archivo/Imagen** — file upload
- **Grid/Tabla** — campo para ingresar datos en formato tabla (filas y columnas)

### Implementación requerida en el editor de formularios:

**Archivos a modificar:**
```
wm_frontend/src/app/modules/admin-depto/pages/formularios/
  - formulario-editor.component.ts
  - formulario-editor.component.html
  - formulario-editor.component.scss

wm_backend/.../formulario/model/Formulario.java
  → El campo "tipo" en CampoFormulario debe incluir todos los tipos nuevos
```

**Los tipos de campo deben ser:**
```typescript
// En el modelo TypeScript de campo de formulario:
tipo: 'TEXTO' | 'TEXTAREA' | 'NUMERO' | 'FECHA' | 'SELECCION' |
      'RADIO' | 'CHECKBOX' | 'ARCHIVO' | 'IMAGEN' | 'ETIQUETA' | 'GRID'
```

**El editor de formularios debe verse así (como Google Forms):**

```
┌─────────────────────────────────────────────────────────────┐
│  Formulario: Recibir solicitud del cliente                  │
│                                                             │
│  COMPONENTES DISPONIBLES (arrastrar o hacer click):        │
│  [Etiqueta] [Texto] [Textarea] [Número] [Fecha]            │
│  [Radio] [Checkbox] [Select] [Archivo] [Imagen] [Grid]     │
├─────────────────────────────────────────────────────────────┤
│  CAMPOS DEL FORMULARIO:                                     │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 📝 Nombre del titular              [Texto]    [✏️][🗑️]│   │
│  │    Requerido: ✓   Es campo prioridad: ✗            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 📄 Observaciones adicionales    [Textarea]  [✏️][🗑️]│   │
│  │    Requerido: ✗   Filas: 4                         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 🔘 Tipo de medidor              [Radio]     [✏️][🗑️]│   │
│  │    Opciones: Monofásico | Trifásico                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  [+ Agregar campo]              [💾 Guardar formulario]    │
└─────────────────────────────────────────────────────────────┘
```

**Al hacer click en "+ Agregar campo" o en un tipo de componente:**
- Aparece el campo al final de la lista
- Se puede editar: etiqueta, placeholder, si es requerido, opciones (para Radio/Select/Checkbox)
- Para TEXTAREA: configurar número de filas (default: 3)
- Para GRID: configurar columnas (nombre de cada columna)
- Para RADIO y CHECKBOX: agregar/quitar opciones dinámicamente
- Botón de subir/bajar para reordenar campos
- Botón de eliminar con confirmación

**El campo GRID debe funcionar así:**
```
Al ejecutar una tarea con campo GRID, el funcionario ve:
┌──────────────┬──────────────┬──────────────┐
│ Descripción  │ Cantidad     │ Precio (Bs)  │
├──────────────┼──────────────┼──────────────┤
│ [_________]  │ [_________]  │ [_________]  │  ← fila editable
└──────────────┴──────────────┴──────────────┘
[+ Agregar fila]
```

**Renderizado dinámico del formulario al ejecutar tarea:**

En el componente donde el funcionario rellena el formulario
(`ejecutar-tarea.component.ts` o similar), agregar el renderizado para cada tipo:

```typescript
// Para cada campo según su tipo, renderizar el componente correcto:

// ETIQUETA → <label> solo texto, sin input
// TEXTO → <input type="text">
// TEXTAREA → <textarea [rows]="campo.filas || 3">
// NUMERO → <input type="number">
// FECHA → <input type="date"> o date picker
// SELECCION → <select> con <option> por cada opción
// RADIO → <input type="radio"> agrupados por nombre del campo
// CHECKBOX → <input type="checkbox"> múltiple, guarda array de valores
// ARCHIVO → <input type="file"> con subida a backend
// IMAGEN → <input type="file" accept="image/*"> con preview
// GRID → tabla dinámica con filas agregables
```

**En Flutter (ejecutar_tarea_screen.dart):**
Agregar los mismos tipos en el switch que renderiza campos:
```dart
case 'TEXTAREA':
  return TextFormField(
    maxLines: campo.filas ?? 3,
    decoration: InputDecoration(labelText: campo.etiqueta),
  );
case 'RADIO':
  return Column(
    children: campo.opciones.map((op) => RadioListTile(
      title: Text(op),
      value: op,
      groupValue: _respuestas[campo.nombre],
      onChanged: (val) => setState(() => _respuestas[campo.nombre] = val),
    )).toList(),
  );
case 'CHECKBOX':
  return Column(
    children: campo.opciones.map((op) => CheckboxListTile(
      title: Text(op),
      value: (_respuestas[campo.nombre] as List?)?.contains(op) ?? false,
      onChanged: (val) => setState(() {
        final lista = List<String>.from(_respuestas[campo.nombre] ?? []);
        val! ? lista.add(op) : lista.remove(op);
        _respuestas[campo.nombre] = lista;
      }),
    )).toList(),
  );
```

**En el backend, actualizar el enum/string de tipos permitidos:**
```
wm_backend/src/main/java/com/workflow/formulario/model/CampoFormulario.java
→ El campo "tipo" debe aceptar: TEXTO, TEXTAREA, NUMERO, FECHA, SELECCION,
  RADIO, CHECKBOX, ARCHIVO, IMAGEN, ETIQUETA, GRID
```

**Actualizar el seeder** para que los formularios usen TEXTAREA y RADIO en algunos campos:
```
En DataSeeder.java, para la política "Instalación de Nuevo Medidor":
  Nodo "Verificar documentación":
    - Campo "observaciones": tipo TEXTAREA, filas: 4, requerido: false
  
  Nodo "Inspección técnica" (Técnico):
    - Campo "resultado_inspeccion": tipo RADIO, opciones: ["Viable", "No viable"]
    - Campo "observaciones_tecnicas": tipo TEXTAREA, filas: 3

Para la política "Reclamo por Facturación":
  Nodo "Registrar reclamo":
    - Campo "descripcion_reclamo": tipo TEXTAREA, filas: 5, requerido: true
    - Campo "items_facturados": tipo GRID,
      columnas: ["Período", "Consumo (kWh)", "Monto (Bs)"]
```

---

## CRITERIO 4 (20pts) — Cuellos de botella con IA (capas y mediciones)

### Lo que Martinez verifica:
- Existe una pantalla de análisis de IA accesible desde el sidebar
- Se puede seleccionar una política y analizarla
- Muestra GRÁFICAMENTE cuáles nodos son cuellos de botella
- Hay métricas visibles: tiempo promedio, tasa de rechazo, carga de trabajo
- Hay sugerencias de mejora por nodo

### Verificar que existe la página de análisis:
```
Ruta: http://localhost:4200/admin/analisis
Sidebar Admin General → debe tener "📊 Análisis IA"
```

### Si no existe o está incompleta, crear/completar:

**Archivos:**
```
wm_frontend/src/app/modules/admin/pages/analisis/
  - analisis-ia.component.ts
  - analisis-ia.component.html
  - analisis-ia.component.scss
```

**La pantalla debe mostrar:**

```
┌──────────────────────────────────────────────────────────────┐
│  📊 Análisis de Cuellos de Botella                           │
│                                                              │
│  Política: [Baja de Servicio ▼]    [🔍 Analizar ahora]      │
├──────────────────────────────────────────────────────────────┤
│  RESUMEN:  3 nodos analizados  |  1 cuello crítico           │
│                                                              │
│  ████████████████░░░░  Verificar documentación    87% 🔴     │
│  ████████░░░░░░░░░░░░  Inspección técnica          52% 🟡     │
│  ███░░░░░░░░░░░░░░░░░  Registrar pago              18% 🟢     │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 🔴 Verificar documentación — CRÍTICO               │    │
│  │ Tiempo promedio: 8.2h  Tasa rechazo: 35%           │    │
│  │ 💡 Dividir la tarea en pasos más pequeños          │    │
│  │ 💡 Capacitar al personal en los criterios          │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

**Las barras de probabilidad son CSS puro (sin librería):**
```scss
.barra-fill {
  height: 24px;
  border-radius: 4px;
  transition: width 0.8s ease;
  &.critica { background: linear-gradient(90deg, #f44250, #ff6b6b); }
  &.alta    { background: linear-gradient(90deg, #ff9800, #ffb74d); }
  &.media   { background: linear-gradient(90deg, #fecc1b, #ffe082); }
  &.baja    { background: linear-gradient(90deg, #6bd968, #a5d6a7); }
}
```

**El botón "Analizar ahora" llama a:**
```
POST /api/v1/ia/analizar-politica
Body: { "politicaId": "..." }
```

**Si el endpoint retorna vacío o error 500**, verificar en:
```
wm_backend/.../ia/IaController.java   → debe retornar ResponseEntity con body
wm_backend/.../ia/IaService.java      → analizarPolitica() no debe retornar null
workflow-ia/app/routers/analisis.py   → debe retornar JSON con resultados
workflow-ia/app/services/analisis_service.py → modelo cargado correctamente
```

**Para que haya datos suficientes para el análisis**, crear al menos 3 trámites
completados en la política antes de la demostración:
- Completar el flujo completo de "Baja de Servicio" 3 veces
- El microservicio IA calcula métricas reales de esos trámites

---

## CRITERIO 5 (20pts) — IA en el diagramador Y en el llenado del formulario

### Martinez evalúa DOS usos de IA:

**5A — IA en el diagramador (generar diagrama desde texto/voz):**

Verificar que al crear una política nueva:
1. Aparece el textarea de descripción
2. Hay botón "🎤 Grabar" que activa el micrófono (Web Speech API)
3. Hay botón "✨ Generar con IA"
4. Al hacer click → aparece el diagrama en el editor automáticamente
5. El diagrama es editable (se puede mover, agregar, eliminar nodos)

**Archivos:**
```
wm_frontend/src/app/modules/admin/pages/politicas/
  - nueva-politica.component.ts   → flujo crear → generar con IA → editor
  - nueva-politica.component.html → botones Grabar + Generar con IA + Manual
```

**El botón de voz usa Web Speech API (nativa del navegador, sin backend):**
```typescript
iniciarGrabacion(): void {
  const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
  if (!SR) { alert('Usa Chrome o Edge para reconocimiento de voz'); return; }
  
  this.recognition = new SR();
  this.recognition.lang = 'es-BO';
  this.recognition.continuous = true;
  this.recognition.interimResults = true;
  
  this.recognition.onresult = (event: any) => {
    let texto = '';
    for (let i = 0; i < event.results.length; i++) {
      texto += event.results[i][0].transcript;
    }
    this.descripcion = texto; // actualiza el textarea en tiempo real
  };
  
  this.recognition.start();
  this.grabando = true;
}
```

**5B — IA durante el llenado del formulario (sugerencias inteligentes):**

Martinez espera que la IA también ayude DURANTE el llenado del formulario.
Esto significa: cuando el funcionario está completando una tarea, la IA puede
sugerir valores o autocompletar campos basándose en el contexto del trámite.

**Implementación mínima para pasar este criterio:**

En el componente de ejecución de tarea, agregar un botón pequeño junto a cada
campo de tipo TEXTO o TEXTAREA:

```
[Nombre del titular *    ]  [✨ Sugerir]
```

Al hacer click en "✨ Sugerir":
1. Enviar al microservicio IA: el nombre del campo + el contexto del trámite
2. El microservicio retorna una sugerencia de texto
3. El campo se rellena con la sugerencia (el usuario puede editar)

**Endpoint nuevo en FastAPI:**
```
POST /ia/sugerir-campo
Body: {
  "nombre_campo": "observaciones_tecnicas",
  "tipo_nodo": "Inspección técnica",
  "nombre_politica": "Instalación de Nuevo Medidor",
  "contexto": "Instalación monofásica en zona residencial"
}
Response: { "sugerencia": "La instalación cumple con los requisitos técnicos..." }
```

**Implementación en FastAPI (app/routers/formulario.py):**
```python
@router.post("/sugerir-campo")
async def sugerir_campo(request: SugerirCampoRequest):
    try:
        prompt = f"""Eres asistente para formularios de procesos empresariales.
        
Tarea actual: {request.tipo_nodo}
Proceso: {request.nombre_politica}
Campo a completar: {request.nombre_campo}
Contexto adicional: {request.contexto or 'Sin contexto'}

Genera una sugerencia breve y profesional para este campo (máximo 2 oraciones).
Responde SOLO con el texto sugerido, sin explicaciones."""

        respuesta = groq_client.chat.completions.create(
            model="llama-3.1-8b-instant",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.3,
            max_tokens=150
        )
        return {"sugerencia": respuesta.choices[0].message.content.strip()}
    except Exception as e:
        return {"sugerencia": ""}  # fallback silencioso
```

**En Angular, en el componente de ejecutar tarea:**
```typescript
sugerirCampo(campo: CampoFormulario): void {
  this.http.post<{sugerencia: string}>(`${environment.apiUrl}/api/v1/ia/sugerir-campo`, {
    nombre_campo: campo.nombre,
    tipo_nodo: this.ejecucion.nombreNodo,
    nombre_politica: this.ejecucion.nombrePolitica,
    contexto: this.respuestas[campo.nombre] || ''
  }).subscribe({
    next: (r) => {
      if (r.sugerencia) {
        this.respuestas[campo.nombre] = r.sugerencia;
      }
    },
    error: () => {} // silencioso
  });
}
```

**En Flutter (ejecutar_tarea_screen.dart):**
Agregar un IconButton junto a los campos TEXTO y TEXTAREA:
```dart
IconButton(
  icon: const Icon(Icons.auto_awesome, color: Color(0xFF9D9D60)),
  tooltip: 'Sugerir con IA',
  onPressed: () => _sugerirCampo(campo),
)

Future<void> _sugerirCampo(Campo campo) async {
  final response = await _api.post('/api/v1/ia/sugerir-campo', {
    'nombre_campo': campo.nombre,
    'tipo_nodo': widget.nombreNodo,
    'nombre_politica': widget.nombrePolitica,
    'contexto': _respuestas[campo.nombre] ?? ''
  });
  if (response['sugerencia'] != null && response['sugerencia'].isNotEmpty) {
    setState(() => _respuestas[campo.nombre] = response['sugerencia']);
  }
}
```

---

## RESUMEN DE ARCHIVOS A VERIFICAR/MODIFICAR

```
CRITERIO 2 — Diagrama UML:
  wm_frontend/.../editor-diagrama/diagram-canvas.component.ts
  wm_frontend/.../editor-diagrama/uml-shapes.constants.ts

CRITERIO 3 — Formularios dinámicos (MÁS IMPORTANTE):
  wm_frontend/.../admin-depto/pages/formularios/formulario-editor.component.ts
  wm_frontend/.../admin-depto/pages/formularios/formulario-editor.component.html
  wm_frontend/.../funcionario/pages/tareas/ejecutar-tarea.component.ts
  wm_frontend/.../funcionario/pages/tareas/ejecutar-tarea.component.html
  wm_mobile/lib/screens/funcionario/ejecutar_tarea_screen.dart
  wm_backend/.../formulario/model/CampoFormulario.java  (agregar tipos nuevos)
  wm_backend/.../config/DataSeeder.java  (actualizar con TEXTAREA, RADIO, GRID)

CRITERIO 4 — Análisis IA:
  wm_frontend/.../admin/pages/analisis/analisis-ia.component.ts (crear si no existe)
  wm_frontend/.../admin/pages/analisis/analisis-ia.component.html
  wm_backend/.../ia/IaController.java
  wm_backend/.../ia/IaService.java
  workflow-ia/app/routers/analisis.py
  workflow-ia/app/services/analisis_service.py

CRITERIO 5 — IA en diagramador Y formularios:
  wm_frontend/.../admin/pages/politicas/nueva-politica.component.ts (voz + generar)
  wm_frontend/.../funcionario/pages/tareas/ejecutar-tarea.component.ts (botón sugerir)
  wm_mobile/lib/screens/funcionario/ejecutar_tarea_screen.dart (sugerir en Flutter)
  workflow-ia/app/routers/formulario.py (endpoint /sugerir-campo)
```

---

## ORDEN DE IMPLEMENTACIÓN (de mayor a menor impacto en la nota)

```
1. CRITERIO 3 — Formularios: agregar TEXTAREA, RADIO, CHECKBOX, GRID al editor
   (el otro grupo perdió 20pts exactamente por esto)

2. CRITERIO 5B — Botón "✨ Sugerir" en formularios con IA
   (diferencia entre 80 y 100 puntos)

3. CRITERIO 4 — Verificar que el dashboard de análisis funciona y se ve bien
   con gráficos de barras y sugerencias

4. CRITERIO 5A — Verificar que generar diagrama por voz funciona en Chrome

5. CRITERIO 1 — Verificar flujo completo sin errores antes del examen
```

---

## PARA LA DEMO CON MARTINEZ (flujo sugerido de 10 minutos)

```
1. [2 min] Abrir el frontend desplegado en Azure → login como Admin General
2. [2 min] Crear nueva política → escribir descripción → click "Generar con IA"
            → mostrar el diagrama generado → editarlo → guardarlo
3. [1 min] Activar la política → iniciar un trámite
4. [2 min] En otra pestaña: login como Funcionario → abrir tarea
            → mostrar formulario dinámico con TEXTAREA y RADIO
            → click "✨ Sugerir" en un campo → la IA rellena el campo
            → completar el formulario
5. [1 min] Volver al monitor → mostrar que cambió de color en tiempo real (WebSocket)
6. [2 min] Ir a "Análisis IA" → analizar la política → mostrar barras de cuellos de botella
            → mostrar sugerencias de mejora
```
