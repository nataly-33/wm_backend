# GUÍA DE IMPLEMENTACIÓN — PARTE 1
## Estado actual y correcciones (Días 1–6)
### WorkflowManager · Parcial 1 SW1

---

> **Cómo usar esta guía:**
> Pásala directamente a la IA de codificación. Indica en qué día estás trabajando.
> Esta parte cubre TODO lo que debe estar correcto antes de avanzar al Día 7.
> Los días 1–5 ya implementados se revisan con correcciones; el Día 6 se reescribe desde cero.

---

## DECISIONES DE ARQUITECTURA DEFINIDAS

Estas decisiones afectan a todos los días y deben respetarse:

| Decisión | Elección | Motivo |
|----------|----------|--------|
| Editor de diagrama | `@maxgraph/core` (sucesor de mxGraph) | Control total, UML 2.5 real, mismo motor que draw.io |
| Carriles del diagrama | **VERTICALES** (columnas), no horizontales | Estándar UML 2.5 Activity Diagram |
| Asignación de nodo a carril | **Por posición visual**: el nodo se asigna al carril en cuya área fue soltado | UX natural, sin popups intermedios |
| Guardado del diagrama | **Ambos**: colecciones separadas (motor) + JSON snapshot (editor) | El motor usa nodos/transiciones; el editor reconstruye desde JSON |
| Voz en IA | **Voz → transcripción → texto → pipeline NLP** (mismo flujo que texto) | Más rápido, un solo pipeline |
| Seeder | **Un solo seeder completo** basado en CRE Santa Cruz con datos realistas | Facilita demostración a Martinez |

---

## CORRECCIONES DÍAS 1–3 (ya implementados)

### CORRECCIÓN — Flujo de usuarios y departamentos

**Problema:** La relación usuario↔departamento no respeta la jerarquía correcta.

**Flujo correcto que DEBE implementarse:**

```
1. Admin General crea departamentos (sin admin asignado aún)
2. Admin General crea usuarios y los asigna a un departamento
   - Al crear usuario con rol ADMIN_DEPARTAMENTO:
     → el backend actualiza automáticamente departamento.admin_departamento_id
   - Al crear usuario con rol FUNCIONARIO:
     → solo se guarda departamento_id en el usuario
3. Un departamento SIN admin asignado NO puede usarse en un diagrama
   (validación al agregar carril)
4. Un departamento SIN admin asignado NO puede usarse en una política activa
   (validación al activar política)
```

**Cambios en el backend — `UsuarioService.java`:**

En el método `crear(UsuarioRequest request)`:

```
LÓGICA ADICIONAL AL CREAR USUARIO:
- Si request.rol == ADMIN_DEPARTAMENTO:
    1. Verificar que request.departamentoId no sea null
    2. Verificar que el departamento existe y pertenece a la misma empresa
    3. Verificar que el departamento NO tiene ya un admin asignado
       → si tiene, lanzar BadRequestException("Este departamento ya tiene un Admin asignado")
    4. Guardar el usuario
    5. Actualizar departamento.admin_departamento_id = usuario.id recién creado
    6. Guardar el departamento actualizado
    7. Retornar respuesta con los datos del usuario

- Si request.rol == FUNCIONARIO:
    1. Verificar que request.departamentoId no sea null
    2. Verificar que el departamento existe y pertenece a la misma empresa
    3. Guardar el usuario (no actualiza el departamento)

- Si request.rol == ADMIN_GENERAL:
    1. departamentoId debe ser null
    2. Guardar el usuario
```

**Cambios en el backend — `UsuarioService.java` — método `actualizar`:**

```
LÓGICA ADICIONAL AL ACTUALIZAR USUARIO:
- Si el rol cambia de FUNCIONARIO a ADMIN_DEPARTAMENTO:
    → ejecutar misma lógica que al crear ADMIN_DEPARTAMENTO
- Si el rol cambia de ADMIN_DEPARTAMENTO a FUNCIONARIO:
    → limpiar departamento.admin_departamento_id = null del departamento anterior
- Si cambia el departamentoId de un ADMIN_DEPARTAMENTO:
    → limpiar el admin del departamento anterior
    → asignar como admin del nuevo departamento
```

**Endpoint nuevo necesario — `DepartamentoController.java`:**

```
GET /departamentos/empresa/{empresaId}/sin-admin
→ Retorna lista de departamentos activos que tienen admin_departamento_id == null
→ Útil para el front al mostrar qué departamentos aún necesitan un admin

GET /departamentos/empresa/{empresaId}/completos
→ Retorna solo departamentos activos con admin_departamento_id != null
→ Usado al agregar carriles al diagrama (solo estos pueden usarse)
```

**Cambios en el frontend — Pantalla de crear/editar usuario:**

```
FORMULARIO DE USUARIO:
- Campo "Rol": selector ADMIN_GENERAL | ADMIN_DEPARTAMENTO | FUNCIONARIO
- Campo "Departamento": 
    → Se muestra SOLO si el rol es ADMIN_DEPARTAMENTO o FUNCIONARIO
    → Si rol = ADMIN_DEPARTAMENTO: muestra solo departamentos SIN admin asignado
      (usa GET /departamentos/empresa/{id}/sin-admin)
    → Si rol = FUNCIONARIO: muestra TODOS los departamentos activos
    → Si rol = ADMIN_GENERAL: campo oculto, valor null

VALIDACIONES EN EL FRONT:
- ADMIN_DEPARTAMENTO sin departamento seleccionado → error "Debe asignar un departamento"
- FUNCIONARIO sin departamento seleccionado → error "Debe asignar un departamento"
```

**Cambios en el frontend — Pantalla de lista de departamentos:**

```
TABLA DE DEPARTAMENTOS debe mostrar:
- Nombre del departamento
- Nombre del admin asignado (o badge "Sin admin" en color warning si es null)
- Cantidad de funcionarios
- Estado (activo/inactivo)
- Botón editar / eliminar

Al eliminar un departamento:
- Si tiene usuarios asignados → error "No se puede eliminar, tiene usuarios asignados"
- Si tiene admin → limpiar admin_departamento_id antes de eliminar (soft delete)
```

---

## CORRECCIONES DÍAS 4–5 (ya implementados — revisar)

### CORRECCIÓN CRÍTICA — Editor de diagrama

**El editor actual tiene estos problemas confirmados en la imagen:**
1. Carriles horizontales → deben ser **verticales** (columnas)
2. Los nodos no se pueden conectar con flechas
3. Nodos con fondo blanco relleno → deben ser **transparentes con solo borde**
4. No hay validaciones al guardar

**Esta es la reescritura completa del editor. Ver Día 6 en esta guía.**

### CORRECCIÓN — Motor de workflow

**El motor debe manejar correctamente estos casos:**

```
CASO 1 — Trámite llega a nodo en departamento sin funcionarios:
→ Crear ejecucion_nodo con funcionario_id = admin_departamento_id
  (si tampoco hay admin, el trámite queda en PENDIENTE_SIN_ASIGNAR y se notifica al Admin General)

CASO 2 — Nodo PARALELO (Fork):
→ Al completar el nodo anterior al Fork, crear EjecucionNodo en TODOS los nodos destino
→ El trámite.nodo_actual_id queda con un array: nodos_actuales_ids: [id1, id2, id3]
→ El trámite avanza al siguiente nodo solo cuando TODOS los nodos paralelos están COMPLETADO
→ El modelo Tramite debe tener campo: nodos_paralelos_pendientes: [String] (IDs de nodos paralelos activos)

CASO 3 — Nodo DECISION (rombo):
→ Al completar el nodo de decision, el motor lee el campo marcado como es_campo_prioridad=true
→ Compara su valor con las etiquetas de las transiciones salientes del rombo
→ Ejemplo: campo "resultado" = "Aprobado" → busca transición con etiqueta "Aprobado" → va a ese nodo
→ Si ninguna transición coincide → lanzar error y dejar trámite en estado BLOQUEADO

CASO 4 — Ciclo (volver a un nodo anterior):
→ Es una transición normal de tipo LINEAL pero el nodo_destino_id apunta a un nodo ya visitado
→ El motor NO valida si es un ciclo, simplemente crea nueva EjecucionNodo
→ El sistema contabiliza las veces que se ejecutó un nodo (útil para análisis IA)
```

**Campo adicional necesario en el modelo Tramite:**

```json
{
  "nodos_paralelos_pendientes": ["String"],
  "iteraciones_por_nodo": { "nodoId": "numero_de_veces" }
}
```

---

## DÍA 6 — REESCRITURA COMPLETA
## Editor de Diagrama de Actividades UML 2.5 con @maxgraph/core

> Este día reemplaza completamente lo que se hizo antes en el editor.
> El resultado debe verse similar a draw.io pero integrado en la aplicación.

---

### PARTE 6.1 — Instalación y configuración de @maxgraph/core

**Archivo:** `wm_frontend/package.json`

```
Agregar dependencia:
"@maxgraph/core": "^0.10.0"

Ejecutar: npm install @maxgraph/core
```

**Nota importante:** `@maxgraph/core` es el sucesor oficial de `mxGraph` mantenido por la comunidad. Soporta UML 2.5 nativo, swimlanes verticales, y el mismo modelo de datos que draw.io. NO requiere licencia.

---

### PARTE 6.2 — Cambios en el backend para soportar el editor

**Archivo:** `wm_backend/src/main/java/com/workflow/politica/model/Politica.java`

Agregar campo nuevo al modelo:

```
Campo: datos_diagrama_json (tipo: String o Document en MongoDB)
Descripción: Almacena el JSON completo del estado visual del editor (posiciones, estilos, conexiones)
Este campo es usado ÚNICAMENTE por el editor para reconstruir la vista
Los nodos y transiciones en sus colecciones propias siguen siendo la fuente de verdad para el motor
```

**Archivo:** `wm_backend/.../politica/dto/PoliticaResponse.java`

```
Agregar campo: datosDiagramaJson (String)
Se retorna al abrir el editor para que pueda reconstruir el canvas visual
```

**Endpoint nuevo:** `PUT /politicas/{id}/diagrama`

```
Body: { datosDiagramaJson: "...", nodos: [...], transiciones: [...] }
Descripción: Guarda en una sola operación atómica:
  1. El JSON visual en politica.datos_diagrama_json
  2. Los nodos en la colección nodos (upsert: crear si no existe, actualizar si existe)
  3. Las transiciones en la colección transiciones (upsert)
  4. Elimina nodos y transiciones que ya no están en el nuevo estado
Retorna: PoliticaResponse con el estado actualizado
Rol permitido: solo ADMIN_GENERAL
Validaciones:
  - La política debe estar en estado BORRADOR (no se puede editar si está ACTIVA)
```

**Endpoint nuevo:** `GET /politicas/{id}/diagrama`

```
Descripción: Retorna todo lo necesario para abrir el editor:
  - datos_diagrama_json (para reconstruir el canvas visual)
  - nodos[] (con sus propiedades actuales de la BD)
  - transiciones[] (con sus propiedades)
  - departamentos de la empresa (para los carriles disponibles)
Rol permitido: ADMIN_GENERAL, ADMIN_DEPARTAMENTO (solo lectura), FUNCIONARIO (solo lectura)
```

**Endpoint nuevo:** `GET /departamentos/empresa/{empresaId}/completos`

```
Descripción: Solo departamentos que tienen admin_departamento_id asignado
Usado por: el editor al mostrar la lista de carriles disponibles para agregar
Si un departamento no tiene admin, NO aparece en esta lista
```

---

### PARTE 6.3 — Estructura del componente Angular

**Archivos a crear en:** `wm_frontend/src/app/modules/admin/pages/politicas/editor-diagrama/`

```
editor-diagrama.component.ts      → lógica principal del editor
editor-diagrama.component.html    → layout del editor
editor-diagrama.component.scss    → estilos del editor
diagram-toolbar.component.ts      → barra de herramientas superior
diagram-palette.component.ts      → panel izquierdo con paleta UML
diagram-canvas.component.ts       → el canvas mxGraph
diagram-properties.component.ts   → panel derecho de propiedades del nodo
uml-shapes.constants.ts           → definiciones de formas UML 2.5
diagram-validators.ts             → reglas de validación del diagrama
```

---

### PARTE 6.4 — Layout visual del editor

```
┌────────────────────────────────────────────────────────────────────┐
│ BARRA SUPERIOR:  [Nombre política]  [💾 Guardar]  [📷 PNG]  [📄 PDF] │
│                  [✨ Generar con IA]  [🎤 Voz]  [Estado: BORRADOR]   │
├──────────┬─────────────────────────────────────────────────────────┤
│  PALETA  │                    CANVAS                               │
│  UML     │                                                         │
│          │  ┌──── Depto A ────┐  ┌──── Depto B ────┐              │
│ ⬤ INICIO  │  │                │  │                │              │
│           │  │  ⬤            │  │  [Tarea]        │              │
│ □ TAREA   │  │   ↓            │  │     ↓           │              │
│           │  │  [Acción]     │  │  ◆ Decision     │              │
│ ◆ DECISION│  │                │  │    ↙   ↘        │              │
│           │  └────────────────┘  └─────────────────┘              │
│ ▬ PARALELO│                                                        │
│           │  [+ Agregar carril de departamento]                    │
│ ⦿ FIN     │                                                        │
│           │                                                        │
├──────────┴─────────────────────────────────────────────────────────┤
│ PANEL PROPIEDADES (aparece al click en un nodo o transición)       │
│ [Nombre] [Formulario asignado] [Tipo de transición] [Condición]    │
└────────────────────────────────────────────────────────────────────┘
```

---

### PARTE 6.5 — Especificaciones del canvas con @maxgraph/core

**Configuración inicial del canvas:**

```
En diagram-canvas.component.ts, al inicializar:

1. Crear instancia de Graph (mxGraph) sobre el elemento div#canvas
2. Configurar:
   - graph.setConnectable(true) → permite conectar nodos arrastrando desde los puntos de conexión
   - graph.setAllowDanglingEdges(false) → las flechas deben conectar dos nodos (no flotar)
   - graph.setCellsEditable(true) → doble click edita el nombre del nodo
   - graph.setDropEnabled(true) → permite soltar nodos desde la paleta
   - graph.swimlaneNesting = false → swimlanes planos (no anidados)
   - graph.setHtmlLabels(true) → las etiquetas soportan HTML
   - graph.setTooltips(true) → mostrar tooltips

3. Deshabilitar la cuadrícula visible pero mantener snap:
   - graph.setGridEnabled(true)
   - graph.setGridSize(10)

4. Activar el botón "conexión" en el toolbar de mxGraph
   (ConnectionHandler ya está incluido en @maxgraph/core)
```

**Swimlanes (carriles) — configuración:**

```
Los carriles son VERTICALES (columnas), no horizontales.
Cada carril es un contenedor swimlane de mxGraph.

Al agregar un carril:
1. Llamar a GET /departamentos/empresa/{id}/completos para obtener los disponibles
2. Mostrar un selector (dropdown/modal) con la lista de departamentos con admin
3. Al seleccionar uno, crear el swimlane en el canvas:
   - Estilo: swimlane con orientación vertical
   - Label: nombre del departamento (en la parte superior del carril)
   - Ancho: 250px por defecto, redimensionable
   - Alto: ocupa toda la altura del canvas (100%)
4. Guardar la relación carril → departamento_id en el estado local del componente

REGLA: No se puede agregar dos veces el mismo departamento como carril.
REGLA: Si el departamento ya se usó como carril y se intenta agregar de nuevo → toast de error.

Estilo visual del swimlane:
- fillColor: rgba del color del departamento (generar automáticamente por índice)
  Ejemplo: primer carril → rgba(192,192,128,0.15), segundo → rgba(157,157,96,0.15), etc.
- strokeColor: #7A7A40
- fontColor: #f5f5e8
- fontSize: 14
- fontStyle: bold
- startSize: 30 (altura del header del carril)
```

**Formas UML 2.5 — especificación de estilos mxGraph:**

```
Cada forma en la paleta tiene un estilo mxGraph específico.
Al arrastrar y soltar al canvas, se crea un vértice con ese estilo.

INICIO (Initial Node UML 2.5):
  Estilo: "ellipse;fillColor=#333300;strokeColor=#C0C080;fontColor=#f5f5e8;fontSize=11;"
  Tamaño: 40x40
  Label: nombre editable (default: "Inicio")
  Regla: SOLO puede haber UN nodo INICIO por diagrama
  
TAREA (Action Node UML 2.5):
  Estilo: "rounded=1;fillColor=none;strokeColor=#9D9D60;fontColor=#f5f5e8;fontSize=11;arcSize=30;"
  → fillColor=none significa transparente (sin fondo blanco)
  Tamaño: 160x60
  Label: nombre de la acción, editable

DECISION (Decision/Merge Node UML 2.5):
  Estilo: "rhombus;fillColor=none;strokeColor=#9D9D60;fontColor=#f5f5e8;fontSize=11;"
  Tamaño: 80x80
  Label: pregunta o condición (editable), opcional
  Regla: debe tener EXACTAMENTE 1 entrada y 2+ salidas

PARALELO FORK (Fork Node UML 2.5):
  Estilo: "shape=mxgraph.flowchart.or;fillColor=#7A7A40;strokeColor=#C0C080;"
  → O usar una barra negra gruesa: "shape=line;strokeColor=#333300;strokeWidth=8;fillColor=#333300;"
  Tamaño: 150x10 (barra horizontal ancha)
  Label: vacío o "Fork"
  Regla: tiene 1 entrada y 2+ salidas (todas en paralelo)

PARALELO JOIN (Join Node UML 2.5):
  Mismo estilo que Fork pero con 2+ entradas y 1 salida
  El tipo se determina por las conexiones (si tiene más entradas → Join)
  
FIN (Activity Final Node UML 2.5):
  Estilo: "ellipse;fillColor=#333300;strokeColor=#9D9D60;fontSize=11;"
  Con punto interior (círculo dentro de círculo):
  Usar doble elipse: crear un ellipse grande con un ellipse pequeño encima
  O usar: "shape=doubleEllipse;fillColor=#333300;strokeColor=#9D9D60;"
  Tamaño: 40x40
  Regla: puede haber más de un FIN en el diagrama
```

**Conexiones (control flows) — UML 2.5:**

```
Las flechas en UML 2.5 Activity Diagrams son Control Flows.

Estilo de la flecha:
  "edgeStyle=orthogonalEdgeStyle;strokeColor=#C0C080;fontColor=#f5f5e8;
   fontSize=10;endArrow=block;endFill=1;"

Las etiquetas de las flechas salientes de DECISION:
  → Deben mostrarse entre corchetes: [Aprobado] [Rechazado]
  → Al hacer click en la flecha → modal para editar:
     - Tipo: LINEAL | ALTERNATIVA | PARALELA
     - Etiqueta (se muestra en el canvas como [etiqueta])
     - Condición (expresión técnica para el motor, invisible en el canvas)

Para ALTERNATIVA:
  → La etiqueta se muestra entre corchetes automáticamente al renderizar

CÓMO SE CONECTAN LOS NODOS:
  - Al pasar el mouse sobre un nodo → aparecen 4 puntos de conexión (arriba, abajo, izquierda, derecha)
  - El usuario arrastra desde un punto hacia otro nodo
  - @maxgraph/core maneja esto nativamente con ConnectionHandler
  - Al soltar sobre otro nodo → se crea la arista
  - Si se suelta en el vacío → la arista se cancela (no se crean aristas flotantes)
```

**Asignación automática de nodo a carril:**

```
Este es el mecanismo más importante del editor.

Cuando el usuario suelta un nodo en el canvas:
1. Obtener las coordenadas (x, y) del punto donde se soltó
2. Determinar qué carril (swimlane) contiene ese punto:
   → Recorrer los swimlanes en orden
   → Verificar si x está dentro del rango [swimlane.x, swimlane.x + swimlane.width]
3. Si está dentro de un carril:
   → Asignar el nodo como hijo del swimlane en mxGraph
     (graph.addCell(nodoVertex, carrilaVertex))
   → El departamento_id del nodo = departamento_id del carril
4. Si NO está dentro de ningún carril:
   → No crear el nodo
   → Mostrar toast: "Suelta el nodo dentro de un carril de departamento"
   
Nota: Los nodos INICIO y FIN son especiales:
   → Pueden colocarse en cualquier carril
   → O en un área global fuera de los carriles (depende de la implementación)
   → Recomendación: permitirlos en cualquier carril, el motor no requiere que estén
     en un carril específico
```

**Estado local del editor (TypeScript):**

```typescript
// Estructura del estado que maneja el editor en memoria:

interface EstadoEditor {
  politicaId: string;
  carriles: CarrilEstado[];
  nodos: NodoEstado[];
  transiciones: TransicionEstado[];
  modificado: boolean; // true si hay cambios sin guardar
}

interface CarrilEstado {
  departamentoId: string;
  nombreDepartamento: string;
  mxCellId: string; // ID del swimlane en mxGraph
}

interface NodoEstado {
  id?: string; // ID en la BD (undefined si aún no se guardó)
  tempId: string; // ID temporal en el editor
  tipo: 'INICIO' | 'TAREA' | 'DECISION' | 'FIN' | 'PARALELO_FORK' | 'PARALELO_JOIN';
  nombre: string;
  departamentoId: string;
  formularioId?: string;
  posicionX: number;
  posicionY: number;
  mxCellId: string; // ID del vértice en mxGraph
}

interface TransicionEstado {
  id?: string;
  tempId: string;
  nodoOrigenId: string; // tempId del nodo origen
  nodoDestinoId: string;
  tipo: 'LINEAL' | 'ALTERNATIVA' | 'PARALELA';
  etiqueta?: string;
  condicion?: string;
  mxCellId: string;
}
```

---

### PARTE 6.6 — Validaciones del diagrama (antes de guardar)

**Archivo:** `wm_frontend/.../editor-diagrama/diagram-validators.ts`

```
Función: validarDiagrama(estado: EstadoEditor): string[]
Retorna: array de strings con los errores encontrados (vacío = válido)

VALIDACIONES OBLIGATORIAS:

1. INICIO_UNICO:
   - Debe haber exactamente 1 nodo de tipo INICIO
   - Error: "El diagrama debe tener exactamente un nodo de Inicio"

2. FIN_EXISTE:
   - Debe haber al menos 1 nodo de tipo FIN
   - Error: "El diagrama debe tener al menos un nodo de Fin"

3. TAREA_EXISTE:
   - Debe haber al menos 1 nodo de tipo TAREA entre INICIO y FIN
   - Error: "El diagrama debe tener al menos una tarea"

4. NODOS_CONECTADOS:
   - Todos los nodos deben tener al menos 1 transición de entrada O ser el nodo INICIO
   - Todos los nodos deben tener al menos 1 transición de salida O ser un nodo FIN
   - Error: "El nodo '{nombre}' está desconectado"

5. DECISION_CONEXIONES:
   - Todo nodo DECISION debe tener exactamente 1 entrada y 2+ salidas
   - Las salidas de DECISION deben ser de tipo ALTERNATIVA y tener etiqueta
   - Error: "El nodo de Decisión '{nombre}' debe tener 2 o más salidas con etiqueta"

6. PARALELO_CONEXIONES:
   - Fork: 1 entrada, 2+ salidas PARALELA
   - Join: 2+ entradas PARALELA, 1 salida
   - Error: "El nodo Paralelo '{nombre}' no tiene el número correcto de conexiones"

7. NODOS_EN_CARRIL:
   - Todos los nodos TAREA, DECISION, PARALELO deben estar en un carril
   - Error: "El nodo '{nombre}' debe estar dentro de un carril de departamento"

8. DEPARTAMENTOS_CON_ADMIN:
   - Los carriles usados deben corresponder a departamentos con admin asignado
   - Error: "El departamento '{nombre}' no tiene Admin Depto asignado"

9. CAMINO_INICIO_FIN:
   - Debe existir al menos un camino desde INICIO hasta FIN
   - (recorrido BFS/DFS simple)
   - Error: "No existe un camino válido desde Inicio hasta Fin"

VALIDACIONES DE ADVERTENCIA (no bloquean guardar, solo avisan):
- Un nodo TAREA sin formulario asignado → "Advertencia: '{nombre}' no tiene formulario"
- Una transición ALTERNATIVA sin condición técnica → "Advertencia: condición vacía en transición '{etiqueta}'"
```

---

### PARTE 6.7 — Botón guardar y sincronización con el backend

**Flujo al presionar "💾 Guardar":**

```
1. Ejecutar validarDiagrama(estadoActual)
   - Si hay errores → mostrar modal con lista de errores → NO guardar
   - Si hay advertencias → mostrar toast y continuar guardando

2. Serializar el estado visual del canvas:
   const xmlCanvas = graph.toXml() // mxGraph serializa a XML
   const jsonSnapshot = {
     xml: xmlCanvas,
     version: Date.now()
   }

3. Preparar el payload para PUT /politicas/{id}/diagrama:
   {
     datosDiagramaJson: JSON.stringify(jsonSnapshot),
     nodos: [
       {
         id: nodo.id || null, // null si es nuevo
         tempId: nodo.tempId,
         tipo: nodo.tipo,
         nombre: nodo.nombre,
         departamentoId: nodo.departamentoId,
         formularioId: nodo.formularioId || null,
         posicionX: nodo.posicionX,
         posicionY: nodo.posicionY
       }
     ],
     transiciones: [
       {
         id: trans.id || null,
         nodoOrigenTempId: trans.nodoOrigenId,
         nodoDestinoTempId: trans.nodoDestinoId,
         tipo: trans.tipo,
         etiqueta: trans.etiqueta || null,
         condicion: trans.condicion || null
       }
     ]
   }

4. Llamar a PUT /politicas/{id}/diagrama
5. Al recibir respuesta exitosa:
   - Actualizar los ID reales de nodos y transiciones desde la respuesta
   - Marcar estado como modificado=false
   - Mostrar toast: "Diagrama guardado correctamente"
   
6. Manejar error:
   - 400: mostrar mensaje del servidor
   - 409: "La política está activa, no se puede editar el diagrama"
   - 500: "Error al guardar, intenta de nuevo"
```

**Flujo al abrir el editor (cargar diagrama existente):**

```
1. Llamar a GET /politicas/{id}/diagrama
2. Si datosDiagramaJson no está vacío:
   → Parsear el JSON → usar graph.fromXml(jsonSnapshot.xml) para reconstruir el canvas
   → Asignar los IDs reales de BD a los nodos y transiciones del estado local
3. Si datosDiagramaJson está vacío (diagrama nuevo):
   → Canvas vacío
   → Llamar a GET /departamentos/empresa/{id}/completos para cargar los carriles disponibles
4. Cargar la lista de departamentos disponibles para el selector de carriles
```

---

### PARTE 6.8 — Exportación PNG y PDF

```
Botón 📷 PNG:
  1. graph.fit() → ajustar zoom para que todo el diagrama sea visible
  2. Usar html2canvas sobre el div#canvas
  3. canvas.toDataURL('image/png') → crear link de descarga
  4. El nombre del archivo: "{nombre-politica}-diagrama.png"

Botón 📄 PDF:
  1. Igual que PNG para obtener la imagen
  2. Crear nuevo jsPDF con orientación landscape si el diagrama es ancho
  3. pdf.addImage(imgData, 'PNG', 0, 0, width, height)
  4. pdf.save("{nombre-politica}-diagrama.pdf")

Nota: La exportación debe capturar SOLO el canvas, no la paleta ni el panel de propiedades.
Usar: html2canvas(document.querySelector('#diagram-canvas-container'))
```

---

### PARTE 6.9 — Seeder mejorado

**Archivo:** `wm_backend/src/main/java/com/workflow/config/DataSeeder.java`

El seeder debe ejecutarse solo si la BD está vacía. Usar `@PostConstruct` o `ApplicationRunner`.

**Datos que debe crear el seeder:**

```
EMPRESA: "CRE Santa Cruz" (Cooperativa Rural de Electrificación)

DEPARTAMENTOS (todos con admin desde el inicio):
  1. "Atención al Cliente"
  2. "Técnico"
  3. "Facturación"
  4. "Legal"
  5. "Recursos Humanos"

USUARIOS:
  Admin General:
    - nombre: "Admin General Seeder"
    - email: "admin@cre.bo"
    - password: "Admin123!"
    - rol: ADMIN_GENERAL

  Admins de departamento (1 por depto):
    - admin.atencion@cre.bo / Admin123! → Admin de "Atención al Cliente"
    - admin.tecnico@cre.bo / Admin123! → Admin de "Técnico"
    - admin.facturacion@cre.bo / Admin123! → Admin de "Facturación"
    - admin.legal@cre.bo / Admin123! → Admin de "Legal"
    - admin.rrhh@cre.bo / Admin123! → Admin de "Recursos Humanos"

  Funcionarios (2 por depto):
    - func1.atencion@cre.bo / Func123! → Funcionario en "Atención al Cliente"
    - func2.atencion@cre.bo / Func123! → Funcionario en "Atención al Cliente"
    - func1.tecnico@cre.bo / Func123! → Funcionario en "Técnico"
    ... (y así para cada departamento)

POLÍTICA 1: "Instalación de Nuevo Medidor"
  Tipo de flujo: Lineal con una decisión
  Diagrama:
    [INICIO: Atención al Cliente]
    → [TAREA: Recibir solicitud del cliente] (Atención al Cliente)
    → [TAREA: Verificar documentación] (Atención al Cliente)
    → [DECISION: ¿Documentación completa?] (Atención al Cliente)
      → [Aprobado] → [TAREA: Realizar inspección técnica] (Técnico)
                   → [TAREA: Generar presupuesto] (Técnico)
                   → [TAREA: Registrar pago] (Facturación)
                   → [TAREA: Firmar contrato] (Legal)
                   → [FIN]
      → [Rechazado] → [TAREA: Notificar al cliente] (Atención al Cliente)
                    → [FIN]

POLÍTICA 2: "Reconexión de Servicio"
  Tipo de flujo: Paralelo (Fork/Join)
  Diagrama:
    [INICIO: Atención al Cliente]
    → [TAREA: Recibir solicitud de reconexión] (Atención al Cliente)
    → [PARALELO FORK]
      → [TAREA: Verificar deuda pendiente] (Facturación)
      → [TAREA: Verificar estado técnico] (Técnico)
    → [PARALELO JOIN]
    → [DECISION: ¿Todo aprobado?]
      → [Aprobado] → [TAREA: Ejecutar reconexión] (Técnico) → [FIN]
      → [Rechazado] → [TAREA: Informar impedimento] (Atención al Cliente) → [FIN]

POLÍTICA 3: "Baja de Servicio"
  Tipo de flujo: Lineal simple
  Diagrama:
    [INICIO] → [TAREA: Recibir solicitud de baja] (Atención al Cliente)
    → [TAREA: Verificar saldo cero] (Facturación)
    → [TAREA: Retirar medidor] (Técnico)
    → [TAREA: Liquidar contrato] (Legal)
    → [FIN]

POLÍTICA 4: "Reclamo por Facturación"
  Tipo de flujo: Con ciclo (iteración)
  Diagrama:
    [INICIO] → [TAREA: Registrar reclamo] (Atención al Cliente)
    → [TAREA: Analizar consumo histórico] (Facturación)
    → [DECISION: ¿Error confirmado?]
      → [Sí] → [TAREA: Emitir nota de crédito] (Facturación) → [FIN]
      → [No] → [TAREA: Explicar al cliente] (Atención al Cliente)
             → [DECISION: ¿Cliente acepta?]
               → [Sí] → [FIN]
               → [No] → [TAREA: Elevar a supervisor] (Recursos Humanos)
                       → [TAREA: Analizar consumo histórico] (Facturación)
                       → (ciclo: vuelve al primer DECISION)

Para cada política, crear también:
  - Al menos 2 trámites con datos de prueba (uno COMPLETADO, uno EN_PROCESO)
  - Ejecuciones de nodo correspondientes para cada trámite
  - Formularios con campos reales para cada nodo TAREA:
    Ejemplo para "Recibir solicitud del cliente":
      - campo: "nombre_titular", tipo: TEXTO, requerido: true
      - campo: "ci_titular", tipo: TEXTO, requerido: true
      - campo: "direccion_instalacion", tipo: TEXTO, requerido: true
      - campo: "tipo_medidor", tipo: SELECCION, opciones: ["Monofásico", "Trifásico"]
      - campo: "foto_predio", tipo: IMAGEN, requerido: false
```

---

### PARTE 6.10 — Verificación del Día 6

Al finalizar el Día 6, el sistema debe pasar estas pruebas:

```
PRUEBA 1 — Editor básico:
  □ Abrir el editor de una política en BORRADOR
  □ Ver carriles verticales para los departamentos disponibles
  □ Arrastrar INICIO al carril "Atención al Cliente" → se posiciona correctamente
  □ Arrastrar TAREA al mismo carril → aparece sin fondo blanco, solo borde
  □ Arrastrar DECISION al carril "Técnico" → aparece como rombo sin fondo
  □ Arrastrar FIN al último carril
  □ Conectar INICIO → TAREA: pasar el mouse sobre INICIO → aparecen puntos de conexión
    → arrastrar hasta TAREA → aparece la flecha
  □ Guardar → no hay errores de validación → toast "Guardado correctamente"
  □ Cerrar y reabrir → el diagrama se reconstruye igual

PRUEBA 2 — Validaciones:
  □ Intentar guardar sin nodo INICIO → error claro
  □ Intentar guardar sin nodo FIN → error claro
  □ Intentar guardar con nodo desconectado → error claro
  □ Intentar agregar un carril de departamento sin admin → no aparece en la lista

PRUEBA 3 — Seeder:
  □ Ejecutar el seeder → se crean todos los datos
  □ Iniciar sesión con admin@cre.bo / Admin123! → accede como Admin General
  □ Iniciar sesión con func1.tecnico@cre.bo / Func123! → accede como Funcionario
  □ Abrir la política "Instalación de Nuevo Medidor" → el diagrama carga correctamente

PRUEBA 4 — Exportación:
  □ Exportar PNG → descarga imagen del diagrama
  □ Exportar PDF → descarga PDF del diagrama
```

---

## RESUMEN DE ARCHIVOS AFECTADOS (Días 1–6)

### Backend (wm_backend)
```
MODIFICADOS:
  - Empresa.java (modelo)
  - Usuario.java (modelo) — agregar departamentoId como campo obligatorio
  - Departamento.java (modelo) — admin_departamento_id se actualiza automáticamente
  - Politica.java (modelo) — agregar datos_diagrama_json
  - Tramite.java (modelo) — agregar nodos_paralelos_pendientes, iteraciones_por_nodo
  - UsuarioService.java — lógica de asignación automática de admin a departamento
  - MotorWorkflowService.java — manejo de Fork/Join paralelo y ciclos

NUEVOS:
  - PoliticaDiagramaController.java — endpoints PUT/GET /politicas/{id}/diagrama
  - DepartamentoController.java — GET /completos y GET /sin-admin
  - DataSeeder.java — seeder completo con 4 políticas
```

### Frontend (wm_frontend)
```
MODIFICADOS:
  - usuario.service.ts — lógica actualizada para roles y departamentos
  - usuario-form.component — selector condicional de departamento por rol

NUEVOS:
  - editor-diagrama.component.ts/html/scss
  - diagram-toolbar.component.ts
  - diagram-palette.component.ts
  - diagram-canvas.component.ts
  - diagram-properties.component.ts
  - uml-shapes.constants.ts
  - diagram-validators.ts
  
```
---

*Continúa en PARTE 2: Días 7–18 (semana 2 y 2.5)*
