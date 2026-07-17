# Guía de Archivos — wm_frontend

Esta guía responde la pregunta: **"¿Qué archivo tengo que tocar si quiero cambiar X en la pantalla?"**

Organizada por página/sección visible. Cada entrada indica el archivo `.ts`, el archivo `.html` y el `.scss` que lo controlan.

---

## Estructura de rutas del sistema

```
/login                          → Página de inicio de sesión
/admin/...                      → Solo ADMIN_GENERAL
/admin-depto/...                → Solo ADMIN_DEPARTAMENTO
/funcionario/...                → Solo FUNCIONARIO
```

La protección de rutas por rol está en:
- [src/app/core/auth/auth.guard.ts](src/app/core/auth/auth.guard.ts) — Redirige a /login si no hay JWT
- [src/app/app.routes.ts](src/app/app.routes.ts) — Define qué rol puede acceder a cada ruta

---

## PÁGINAS — Admin General (`/admin/`)

### Login
**Ruta:** `/login`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (validación, llamada API) | [src/app/modules/auth/login/login.component.ts](src/app/modules/auth/login/login.component.ts) |
| HTML (formulario, textos, logo) | [src/app/modules/auth/login/login.component.html](src/app/modules/auth/login/login.component.html) |
| Estilos (colores, layout) | [src/app/modules/auth/login/login.component.scss](src/app/modules/auth/login/login.component.scss) |
| Servicio que llama al backend | [src/app/core/services/auth.service.ts](src/app/core/services/auth.service.ts) |

---

### Dashboard — Admin General
**Ruta:** `/admin/dashboard`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (stats, contadores) | [src/app/modules/admin/pages/dashboard/dashboard.component.ts](src/app/modules/admin/pages/dashboard/dashboard.component.ts) |
| HTML (tarjetas de resumen, gráficas) | [src/app/modules/admin/pages/dashboard/dashboard.component.html](src/app/modules/admin/pages/dashboard/dashboard.component.html) |
| Estilos | [src/app/modules/admin/pages/dashboard/dashboard.component.scss](src/app/modules/admin/pages/dashboard/dashboard.component.scss) |

---

### Departamentos
**Ruta:** `/admin/departamentos`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (CRUD, tabla, modales) | [src/app/modules/admin/pages/departamentos/departamentos.component.ts](src/app/modules/admin/pages/departamentos/departamentos.component.ts) |
| HTML (tabla, botones, modal de crear/editar) | [src/app/modules/admin/pages/departamentos/departamentos.component.html](src/app/modules/admin/pages/departamentos/departamentos.component.html) |
| Estilos | [src/app/modules/admin/pages/departamentos/departamentos.component.scss](src/app/modules/admin/pages/departamentos/departamentos.component.scss) |
| Llamadas HTTP al backend | [src/app/core/services/departamento.service.ts](src/app/core/services/departamento.service.ts) |

---

### Usuarios
**Ruta:** `/admin/usuarios`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (tabla, crear usuario, roles) | [src/app/modules/admin/pages/usuarios/usuarios.component.ts](src/app/modules/admin/pages/usuarios/usuarios.component.ts) |
| HTML (tabla, formulario de alta) | [src/app/modules/admin/pages/usuarios/usuarios.component.html](src/app/modules/admin/pages/usuarios/usuarios.component.html) |
| Estilos | [src/app/modules/admin/pages/usuarios/usuarios.component.scss](src/app/modules/admin/pages/usuarios/usuarios.component.scss) |
| Llamadas HTTP al backend | [src/app/core/services/usuario.service.ts](src/app/core/services/usuario.service.ts) |

---

### Formularios — Admin General
**Ruta:** `/admin/formularios`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (CRUD de formularios, campos) | [src/app/modules/admin/pages/formularios/formularios.component.ts](src/app/modules/admin/pages/formularios/formularios.component.ts) |
| HTML (lista de formularios, editor de campos) | [src/app/modules/admin/pages/formularios/formularios.component.html](src/app/modules/admin/pages/formularios/formularios.component.html) |
| Estilos | [src/app/modules/admin/pages/formularios/formularios.component.scss](src/app/modules/admin/pages/formularios/formularios.component.scss) |
| Llamadas HTTP al backend | [src/app/core/services/formulario.service.ts](src/app/core/services/formulario.service.ts) |

---

### Políticas — Lista
**Ruta:** `/admin/politicas`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (tabla de políticas, acciones) | [src/app/modules/admin/pages/politicas/politicas.component.ts](src/app/modules/admin/pages/politicas/politicas.component.ts) |
| HTML (tabla con botones editar/ver/eliminar) | [src/app/modules/admin/pages/politicas/politicas.component.html](src/app/modules/admin/pages/politicas/politicas.component.html) |
| Estilos | [src/app/modules/admin/pages/politicas/politicas.component.scss](src/app/modules/admin/pages/politicas/politicas.component.scss) |
| Llamadas HTTP al backend | [src/app/core/services/politica.service.ts](src/app/core/services/politica.service.ts) |

---

### Nueva Política (con IA y reconocimiento de voz)
**Ruta:** `/admin/politicas/nueva`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica principal (formulario, llamada a IA, progreso) | [src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.ts](src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.ts) |
| HTML (campo descripción, botón Grabar voz, barra de progreso) | [src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.html](src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.html) |
| Estilos | [src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.scss](src/app/modules/admin/pages/politicas/nueva-politica/nueva-politica.component.scss) |
| Servicio IA (llamada a Python) | [src/app/core/services/ia.service.ts](src/app/core/services/ia.service.ts) |

**Funcionalidades especiales en este archivo:**
- **Reconocimiento de voz:** Método `iniciarGrabacion()`. Usa `window.SpeechRecognition` (nativo del browser, sin librería). Solo funciona en Chrome y Edge. El idioma está fijo en `'es-BO'`.
- **Generación con IA:** Llama a `IaService.generarDiagrama()` que hace POST al microservicio Python.
- **Barra de progreso:** Estados `idle → creando → analizando → generando → guardando → listo → error`

---

### Editor de Diagramas (JointJS + voz)
**Ruta:** `/admin/politicas/editor/:id`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica principal (canvas, drag&drop, guardar) | [src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.ts](src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.ts) |
| HTML (toolbar, canvas, panel lateral) | [src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.html](src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.html) |
| Estilos | [src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.scss](src/app/modules/admin/pages/politicas/editor-diagrama/editor-diagrama.component.scss) |
| Formas UML (colores, tamaños, íconos de nodos) | [src/app/modules/admin/pages/politicas/editor-diagrama/uml-shapes.constants.ts](src/app/modules/admin/pages/politicas/editor-diagrama/uml-shapes.constants.ts) |
| Auto-layout (posicionamiento automático de nodos) | [src/app/modules/admin/pages/politicas/editor-diagrama/diagram-layout.util.ts](src/app/modules/admin/pages/politicas/editor-diagrama/diagram-layout.util.ts) |
| Validaciones (reglas UML: todo nodo conectado, etc.) | [src/app/modules/admin/pages/politicas/editor-diagrama/diagram-validators.ts](src/app/modules/admin/pages/politicas/editor-diagrama/diagram-validators.ts) |

**Qué hace cada utility:**

- **`uml-shapes.constants.ts`** — Define cómo se ve cada tipo de nodo en el canvas (colores, formas, puertos de conexión). Si el ingeniero pide cambiar el color de un tipo de nodo, **este es el archivo**.

- **`diagram-layout.util.ts`** — Calcula la posición X/Y de cada nodo cuando se hace "auto-layout". Si el diagrama generado por IA queda mal posicionado, **este es el archivo**.

- **`diagram-validators.ts`** — Valida que el diagrama sea UML válido antes de guardar (cada nodo conectado, exactamente 1 INICIO, al menos 1 FIN, DECISION con 2 salidas). Si se agregan reglas de validación, **este es el archivo**.

**Librerías del canvas:**
- `@joint/core` — Canvas de diagramas con drag&drop. Instalado en `node_modules`. No hay que tocar nada de JointJS directamente.
- `html2canvas` + `jsPDF` — Para exportar el diagrama como imagen o PDF.

---

### Monitor en Tiempo Real
**Ruta:** `/admin/monitor`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (carga políticas, WebSocket, colores de nodos) | [src/app/modules/admin/pages/monitor/monitor.component.ts](src/app/modules/admin/pages/monitor/monitor.component.ts) |
| HTML (selector de política, grid de nodos coloreados) | [src/app/modules/admin/pages/monitor/monitor.component.html](src/app/modules/admin/pages/monitor/monitor.component.html) |
| Estilos (tarjetas de nodo, colores AMARILLO/ROJO/VACIO) | [src/app/modules/admin/pages/monitor/monitor.component.scss](src/app/modules/admin/pages/monitor/monitor.component.scss) |
| Servicio WebSocket (STOMP) | [src/app/core/services/socket.service.ts](src/app/core/services/socket.service.ts) |
| Servicio HTTP para datos del monitor | [src/app/core/services/tramite.service.ts](src/app/core/services/tramite.service.ts) |

**Colores de nodos en el monitor:**
- `AMARILLO` (`#fecc1b`) — Hay trámites en ese nodo
- `ROJO` (`#f44250`) — Hay trámites rechazados o atascados
- `VACIO` (`#565620`) — Sin actividad

Para cambiar los colores: en `monitor.component.ts`, propiedad `colorBorder`.

---

### Trámites — Lista
**Ruta:** `/admin/tramites`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (tabla, filtros, estados) | [src/app/modules/admin/pages/tramites/tramites.component.ts](src/app/modules/admin/pages/tramites/tramites.component.ts) |
| HTML (tabla paginada, badges de estado) | [src/app/modules/admin/pages/tramites/tramites.component.html](src/app/modules/admin/pages/tramites/tramites.component.html) |
| Estilos | [src/app/modules/admin/pages/tramites/tramites.component.scss](src/app/modules/admin/pages/tramites/tramites.component.scss) |
| Llamadas HTTP | [src/app/core/services/tramite.service.ts](src/app/core/services/tramite.service.ts) |

---

### Trámite Detalle
**Ruta:** `/admin/tramites/:id`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (historial de pasos, estado actual) | [src/app/modules/admin/pages/tramites/tramite-detalle/tramite-detalle.component.ts](src/app/modules/admin/pages/tramites/tramite-detalle/tramite-detalle.component.ts) |
| HTML (timeline de ejecuciones, datos del trámite) | [src/app/modules/admin/pages/tramites/tramite-detalle/tramite-detalle.component.html](src/app/modules/admin/pages/tramites/tramite-detalle/tramite-detalle.component.html) |
| Estilos | [src/app/modules/admin/pages/tramites/tramite-detalle/tramite-detalle.component.scss](src/app/modules/admin/pages/tramites/tramite-detalle/tramite-detalle.component.scss) |

---

### Análisis IA (Cuellos de Botella)
**Ruta:** `/admin/analisis`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (selector de política, llamada al análisis ML) | [src/app/modules/admin/pages/analisis/analisis-ia.component.ts](src/app/modules/admin/pages/analisis/analisis-ia.component.ts) |
| HTML (barras de probabilidad, cards de resultados, sugerencias) | [src/app/modules/admin/pages/analisis/analisis-ia.component.html](src/app/modules/admin/pages/analisis/analisis-ia.component.html) |
| Estilos (colores CRITICA/ALTA/MEDIA/BAJA) | [src/app/modules/admin/pages/analisis/analisis-ia.component.scss](src/app/modules/admin/pages/analisis/analisis-ia.component.scss) |
| Servicio (llama al Python) | [src/app/core/services/ia.service.ts](src/app/core/services/ia.service.ts) |

---

## PÁGINAS — Admin Departamento (`/admin-depto/`)

### Dashboard — Admin Depto
**Ruta:** `/admin-depto/dashboard`
| Archivo | Qué controla |
|---------|-------------|
| [src/app/modules/admin-depto/pages/dashboard/dashboard.component.ts](src/app/modules/admin-depto/pages/dashboard/dashboard.component.ts) | Lógica y datos del dashboard del departamento |
| [.html](src/app/modules/admin-depto/pages/dashboard/dashboard.component.html) | Vista |
| [.scss](src/app/modules/admin-depto/pages/dashboard/dashboard.component.scss) | Estilos |

### Formularios — Admin Depto
**Ruta:** `/admin-depto/formularios`
| Archivo | Qué controla |
|---------|-------------|
| [src/app/modules/admin-depto/pages/formularios/formularios.component.ts](src/app/modules/admin-depto/pages/formularios/formularios.component.ts) | Gestión de formularios del departamento |

### Trámites — Admin Depto
**Ruta:** `/admin-depto/tramites`
| Archivo | Qué controla |
|---------|-------------|
| [src/app/modules/admin-depto/pages/tramites/tramites.component.ts](src/app/modules/admin-depto/pages/tramites/tramites.component.ts) | Trámites del departamento |

---

## PÁGINAS — Funcionario (`/funcionario/`)

### Dashboard — Funcionario
**Ruta:** `/funcionario/dashboard`
| Archivo | Qué controla |
|---------|-------------|
| [src/app/modules/funcionario/pages/dashboard/dashboard.component.ts](src/app/modules/funcionario/pages/dashboard/dashboard.component.ts) | Resumen de tareas pendientes y completadas |

### Tareas Pendientes
**Ruta:** `/funcionario/tareas`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (lista de tareas asignadas) | [src/app/modules/funcionario/pages/tareas/tareas.component.ts](src/app/modules/funcionario/pages/tareas/tareas.component.ts) |
| HTML (cards de tarea, botón ejecutar) | [src/app/modules/funcionario/pages/tareas/tareas.component.html](src/app/modules/funcionario/pages/tareas/tareas.component.html) |
| Estilos | [src/app/modules/funcionario/pages/tareas/tareas.component.scss](src/app/modules/funcionario/pages/tareas/tareas.component.scss) |
| Llamadas HTTP | [src/app/core/services/ejecucion.service.ts](src/app/core/services/ejecucion.service.ts) |

### Ejecutar Tarea (Formulario dinámico)
**Ruta:** `/funcionario/tareas/:id`
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (renderizar campos, enviar respuesta) | [src/app/modules/funcionario/pages/ejecutar-tarea/ejecutar-tarea.component.ts](src/app/modules/funcionario/pages/ejecutar-tarea/ejecutar-tarea.component.ts) |
| HTML (switch por tipo de campo: TEXTO/NUMERO/FECHA/SELECCION/ARCHIVO/IMAGEN) | [src/app/modules/funcionario/pages/ejecutar-tarea/ejecutar-tarea.component.html](src/app/modules/funcionario/pages/ejecutar-tarea/ejecutar-tarea.component.html) |
| Estilos | [src/app/modules/funcionario/pages/ejecutar-tarea/ejecutar-tarea.component.scss](src/app/modules/funcionario/pages/ejecutar-tarea/ejecutar-tarea.component.scss) |

---

## COMPONENTES COMPARTIDOS (aparecen en TODAS las páginas autenticadas)
c
### Navbar (barra superior)
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (nombre de usuario, botón logout, notificaciones) | [src/app/shared/components/navbar/navbar.component.ts](src/app/shared/components/navbar/navbar.component.ts) |
| HTML (logo, nombre, íconos) | [src/app/shared/components/navbar/navbar.component.html](src/app/shared/components/navbar/navbar.component.html) |
| Estilos | [src/app/shared/components/navbar/navbar.component.scss](src/app/shared/components/navbar/navbar.component.scss) |

### Sidebar (menú lateral)
| Qué tocar | Archivo |
|-----------|---------|
| Lógica (ítems de menú por rol, ruta activa) | [src/app/shared/components/sidebar/sidebar.component.ts](src/app/shared/components/sidebar/sidebar.component.ts) |
| HTML (lista de links de navegación) | [src/app/shared/components/sidebar/sidebar.component.html](src/app/shared/components/sidebar/sidebar.component.html) |
| Estilos | [src/app/shared/components/sidebar/sidebar.component.scss](src/app/shared/components/sidebar/sidebar.component.scss) |

---

## SERVICIOS — Cuándo y cómo llaman al backend

| Servicio | Endpoint backend que consume | Usado en |
|---------|----------------------------|----------|
| [auth.service.ts](src/app/core/services/auth.service.ts) | `POST /api/v1/auth/login` | Login |
| [usuario.service.ts](src/app/core/services/usuario.service.ts) | `/api/v1/usuarios` | Usuarios page |
| [departamento.service.ts](src/app/core/services/departamento.service.ts) | `/api/v1/departamentos` | Departamentos, dropdowns |
| [politica.service.ts](src/app/core/services/politica.service.ts) | `/api/v1/politicas` | Políticas, editor |
| [nodo.service.ts](src/app/core/services/nodo.service.ts) | `/api/v1/nodos` | Editor diagrama |
| [transicion.service.ts](src/app/core/services/transicion.service.ts) | `/api/v1/transiciones` | Editor diagrama |
| [formulario.service.ts](src/app/core/services/formulario.service.ts) | `/api/v1/formularios` | Formularios, editor |
| [tramite.service.ts](src/app/core/services/tramite.service.ts) | `/api/v1/tramites` | Trámites, monitor |
| [ejecucion.service.ts](src/app/core/services/ejecucion.service.ts) | `/api/v1/ejecuciones` | Tareas funcionario |
| [ia.service.ts](src/app/core/services/ia.service.ts) | `/api/v1/ia/*` (proxy al Python) | Nueva política, análisis |
| [socket.service.ts](src/app/core/services/socket.service.ts) | WebSocket `/ws` (STOMP) | Monitor |

---

## ESTILOS GLOBALES

| Qué tocar | Archivo |
|-----------|---------|
| Variables de color globales (--color-primary, --color-danger, etc.) | [src/styles.scss](src/styles.scss) |
| Configuración de Angular | [src/environments/environment.ts](src/environments/environment.ts) |
| URL del backend (dev) | `src/environments/environment.ts` → `apiUrl` |
| URL del backend (prod) | `src/environments/environment.production.ts` → `apiUrl` |

**Paleta de colores del sistema:**
```scss
--color-bg-dark:    #1a1a00   // Fondo principal
--color-surface:    #2e2e14   // Cards, sidebar
--color-primary:    #C0C080   // Botones primarios, acentos
--color-text:       #f5f5e8   // Texto principal
--color-success:    #6bd968   // Confirmaciones, BAJA severidad
--color-danger:     #f44250   // Errores, CRITICA severidad
--color-warning:    #fecc1b   // Advertencias, ALTA severidad
--color-info:       #3992ff   // Información, anomalías
```
