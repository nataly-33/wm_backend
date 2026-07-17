# Guía de Archivos — wm_mobile

Esta guía responde: **"¿Qué archivo tengo que tocar si quiero cambiar X en la app?"**

---

## Estructura de pantallas

```
/login          → LoginScreen
/tareas         → TareasScreen
/tareas/:id     → EjecutarTareaScreen  (generada dinámicamente)
/monitor        → MonitorScreen
```

Las rutas están definidas en [lib/main.dart](lib/main.dart) en el `MaterialApp`.

---

## PANTALLAS

### Login
**Archivo:** [lib/features/auth/login_screen.dart](lib/features/auth/login_screen.dart)

| Qué tocar | Dónde |
|-----------|-------|
| Textos (título, placeholder, botón) | En el mismo archivo, en los widgets `Text` y `InputDecoration` |
| Color de fondo | Constante `_colorFondo` al inicio del archivo |
| Color del botón principal | Constante `_colorPrimario` |
| Logo o imagen | Widget `Image.asset(...)` en el `build()` |
| Validación del formulario | Método `_validar()` o `_login()` |
| Llamada al backend | Llama a `AuthService.login()` |

**Servicio relacionado:** [lib/core/services/auth_service.dart](lib/core/services/auth_service.dart)

---

### Tareas Pendientes
**Archivo:** [lib/features/tareas/tareas_screen.dart](lib/features/tareas/tareas_screen.dart)

| Qué tocar | Dónde |
|-----------|-------|
| Título de la pantalla (AppBar) | `AppBar(title: Text('...'))`  en el `build()` |
| Card de cada tarea (nombre, estado, tiempo) | Widget `_buildTareaCard(...)` o similar |
| Colores de estado (pendiente, en curso) | Constantes de color al inicio del archivo |
| Texto del botón "Ejecutar" | Dentro del card de tarea |
| Pull-to-refresh | `RefreshIndicator` envolviendo la lista |
| Llamada para cargar tareas | Método `_cargarTareas()` → `ApiService.obtenerEjecuciones()` |

**Servicio relacionado:** [lib/core/services/api_service.dart](lib/core/services/api_service.dart)

---

### Ejecutar Tarea (Formulario Dinámico)
**Archivo:** [lib/features/tareas/ejecutar_tarea_screen.dart](lib/features/tareas/ejecutar_tarea_screen.dart)

Esta pantalla renderiza campos de formulario de forma dinámica según el tipo de cada campo. Es la más compleja de la app.

| Qué tocar | Dónde |
|-----------|-------|
| Cómo se ve un campo TEXTO | Buscar `case 'TEXTO'` o `TipoCampo.TEXTO` |
| Cómo se ve un campo SELECCION (dropdown) | Buscar `case 'SELECCION'` |
| Cómo se ve un campo FECHA | Buscar `case 'FECHA'` |
| Cómo se ve un campo NUMERO | Buscar `case 'NUMERO'` |
| Cómo se ve un campo ARCHIVO | Buscar `case 'ARCHIVO'` → usa `file_picker` |
| Cómo se ve un campo IMAGEN | Buscar `case 'IMAGEN'` → usa `image_picker` |
| Botón "Completar tarea" / "Enviar" | Buscar `ElevatedButton` al final del `build()` |
| Lógica de envío (guardar respuesta) | Método `_guardar()` o `_completar()` |
| Título de la tarea (nombre del nodo) | Dentro del `AppBar` o encabezado de la pantalla |

**Servicios relacionados:**
- [lib/core/services/api_service.dart](lib/core/services/api_service.dart) — Carga el formulario y envía la respuesta
- [lib/core/models/ejecucion_models.dart](lib/core/models/ejecucion_models.dart) — Modelos: `EjecucionDetallada`, `Formulario`, `Campo`

---

### Monitor en Tiempo Real
**Archivo:** [lib/features/monitor/monitor_screen.dart](lib/features/monitor/monitor_screen.dart)

| Qué tocar | Dónde |
|-----------|-------|
| Título de la pantalla | `AppBar(title: Text('...'))`  |
| Cómo se muestra cada nodo (color, nombre) | Widget `_buildNodoCard(...)` o similar |
| Colores (verde/amarillo/rojo) | Constantes o switch por estado al inicio del archivo |
| Selector de política (dropdown) | Widget `DropdownButton` |
| Conexión WebSocket | `SocketService.conectar()` en `initState()` |
| Manejo de eventos en tiempo real | `_socketService.onNodoActualizado(...)` |

**Servicio relacionado:** [lib/core/services/socket_service.dart](lib/core/services/socket_service.dart)

---

## SERVICIOS Y ARCHIVOS DE CONFIGURACIÓN

### URL del backend
**Archivo:** [lib/core/constants/api_url.dart](lib/core/constants/api_url.dart)

```dart
class ApiConstants {
  static const String baseUrl = 'http://10.0.2.2:8080';  // emulador
  // static const String baseUrl = 'http://192.168.x.x:8080';  // físico
  // static const String baseUrl = 'https://tu-app.onrender.com';  // producción
}
```

**Este es el primer archivo a tocar si la app no conecta con el backend.**

---

### Llamadas HTTP al backend
**Archivo:** [lib/core/services/api_service.dart](lib/core/services/api_service.dart)

Contiene todos los métodos que llaman al backend. Si el backend cambia un endpoint, **este es el archivo**.

| Qué hace | Método |
|----------|--------|
| Obtener mis tareas | `obtenerMisEjecuciones()` |
| Obtener detalle de una tarea | `obtenerEjecucionDetallada(id)` |
| Completar una tarea | `completarEjecucion(id, respuesta)` |
| Subir un archivo adjunto | `subirArchivo(bytes, nombre)` |
| Actualizar FCM token | `actualizarFcmToken(userId, fcmToken, jwt)` |
| Obtener monitor de política | `obtenerMonitor(politicaId)` |

---

### Autenticación y sesión
**Archivo:** [lib/core/services/auth_service.dart](lib/core/services/auth_service.dart)

| Qué hace | Método |
|----------|--------|
| Login (llama al backend y guarda el JWT) | `login(email, password)` |
| Logout (borra el JWT guardado) | `logout()` |
| Verificar si hay sesión activa | `isLoggedIn()` |
| Obtener datos del usuario actual | `getCurrentUser()` |
| Sincronizar FCM token con el backend | `sincronizarFcmTokenSesionActiva()` |

El JWT se guarda en `flutter_secure_storage` (almacenamiento cifrado del dispositivo).

---

### Notificaciones Push (FCM)
**Archivo:** [lib/core/services/notification_service.dart](lib/core/services/notification_service.dart)

Ver sección detallada en [GUIA_FUNCIONALIDADES.md](../GUIA_FUNCIONALIDADES.md).

| Qué hace | Método |
|----------|--------|
| Inicializar FCM y pedir permisos | `NotificationService.init()` |
| Obtener el FCM token del dispositivo | `NotificationService.obtenerToken()` |
| Mostrar notificación en foreground | `_mostrarNotificacionLocal(message)` |
| Navegar al abrir una notificación | `_navegarSegunTipo(data)` |

---

### WebSocket (Monitor)
**Archivo:** [lib/core/services/socket_service.dart](lib/core/services/socket_service.dart)

Usa `socket_io_client` para conectarse al backend Java (que usa Socket.IO-compatible STOMP).

| Qué hace | Método |
|----------|--------|
| Conectar al WebSocket | `conectar(politicaId)` |
| Desconectar | `desconectar()` |
| Escuchar actualizaciones de nodos | `onNodoActualizado(callback)` |

---

### Navegación global (para notificaciones)
**Archivo:** [lib/core/services/navigation_service.dart](lib/core/services/navigation_service.dart)

Contiene el `navigatorKey` global que permite navegar desde fuera del árbol de widgets (necesario para navegación desde notificaciones push cuando la app está en background).

---

### Modelos de datos
**Archivo:** [lib/core/models/ejecucion_models.dart](lib/core/models/ejecucion_models.dart)

Contiene las clases de datos:
- `EjecucionDetallada` — Datos de una tarea asignada
- `Formulario` — Formulario dinámico con sus campos
- `Campo` — Un campo del formulario (nombre, tipo, opciones)

**Archivo:** [lib/core/models/auth_models.dart](lib/core/models/auth_models.dart)

- `LoginRequest` — Credenciales de login
- `AuthResponse` — Respuesta del backend (token, datos de usuario)
- `User` — Datos del usuario en sesión

---

## CONFIGURACIÓN DE LA APP

### Punto de entrada
**Archivo:** [lib/main.dart](lib/main.dart)

Aquí se configura:
- Inicialización de Firebase
- Inicialización de NotificationService
- Sincronización del FCM token al arrancar
- `MaterialApp` con rutas y tema visual

**Si se quiere cambiar el color global de la app, el nombre de la app o el tema:** modificar el `ThemeData` en `main.dart`.

```dart
theme: ThemeData(
  primaryColor: const Color(0xFFC0C080),       // Verde oliva
  scaffoldBackgroundColor: const Color(0xFF1a1a00),  // Fondo oscuro
)
```

### Firebase (notificaciones push)
**Archivo:** [lib/firebase_options.dart](lib/firebase_options.dart)

Auto-generado por `flutterfire configure`. **No editar manualmente.** Si se cambia el proyecto de Firebase, regenerar con:
```bash
flutterfire configure
```

### Variables de entorno
**Archivo:** `.env` (no en git) / `.env.production`

```env
API_BASE_URL=https://tu-backend.onrender.com
```

Leídas con `flutter_dotenv` en `main.dart`.

---

## Paleta de colores de la app

```dart
const _colorFondo    = Color(0xFF1a1a00);   // Fondo principal
const _colorCard     = Color(0xFF2e2e14);   // Cards y contenedores
const _colorPrimario = Color(0xFFC0C080);   // Verde oliva (botones, acentos)
const _colorTexto    = Color(0xFFF5F5E8);   // Texto principal
const _colorMuted    = Color(0xFF9D9D60);   // Texto secundario
const _colorBorde    = Color(0xFF565620);   // Bordes de inputs
const _colorError    = Color(0xFFF44250);   // Errores y rechazos
```

Para cambiar colores en una pantalla específica, buscar estas constantes al inicio del archivo `.dart` de esa pantalla.
