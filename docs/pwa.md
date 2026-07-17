# Guía y Explicación de la PWA (Progressive Web App) en WorkflowManager

Esta guía detalla cómo funciona la PWA implementada en el proyecto, cómo probarla y qué funcionalidades están disponibles (o no) cuando el sistema pierde conexión a internet (modo offline).

## 1. ¿Cómo funciona la PWA?

Una PWA convierte una aplicación web tradicional en una experiencia similar a una aplicación nativa. Esto se logra principalmente gracias al **Service Worker (SW)**.
El Service Worker es un script que el navegador ejecuta en segundo plano. Actúa como un intermediario o proxy entre la aplicación web y la red.

En nuestro proyecto, el Service Worker está configurado (vía `ngsw-config.json` y Angular) para:
- **Caché estático:** Descargar y guardar los archivos base de la aplicación (HTML, CSS, JS, imágenes, iconos). Esto garantiza que la interfaz de usuario cargue inmediatamente incluso sin internet.
- **Caché de datos (Data Groups):** Interceptar llamadas a la API (como `/api/v1/documentos`, `/api/v1/politicas`, `/api/v1/tramites`) y guardar las respuestas. Si te quedas sin internet, el Service Worker devolverá la última respuesta guardada en caché.

---

## 2. ¿Cómo probar la PWA?

El Service Worker **no se activa en el modo de desarrollo habitual** (`ng serve`). Para probarlo correctamente:

### Opción A: Compilar para producción y servir localmente
1. Abre una terminal en `wm_frontend`.
2. Compila el proyecto: `ng build`
3. Instala un servidor HTTP ligero si no lo tienes: `npm install -g http-server`
4. Sirve la carpeta generada: `http-server -p 8080 -c-1 dist/wm-frontend/browser`
5. Abre en tu navegador `http://localhost:8080`.
6. Abre las **Herramientas de Desarrollador (F12)** -> Pestaña **Application** -> **Service Workers**. Deberías ver el SW activo.
7. Marca la casilla **"Offline"** en esa misma pestaña (o en la pestaña Network) para simular que no hay internet.
8. Recarga la página y navega. Verás que la app sigue cargando.

### Opción B: Instalar la app (Desktop/Móvil)
Si la aplicación está servida bajo HTTPS (o en localhost), verás un icono en la barra de direcciones del navegador (Chrome/Edge) para **"Instalar aplicación"**. Esto creará un acceso directo en tu escritorio/pantalla de inicio y abrirá la app en una ventana independiente, sin la interfaz del navegador.

---

## 3. ¿Qué funciona y qué NO funciona sin internet?

La regla general de una PWA básica es que las **lecturas (GET) cacheadas funcionan**, pero las **escrituras o servicios externos (POST, PUT, DELETE, websockets) fallan** sin conexión.

### ✅ Lo que SÍ funciona (si ya fue cargado antes):
- **Navegación por el sistema:** Puedes moverte entre las pantallas de "Mis Trámites", "Documentos", "Reportes", etc.
- **Ver el listado de Trámites:** Si ya habías entrado a esa página con internet, el SW guardó la lista y te la mostrará.
- **Ver Documentos y Políticas:** Las listas cacheadas seguirán visibles.

### ❌ Lo que NO funciona sin internet:

#### 1. Iniciar un trámite o enviar formularios
- **No es posible.** Iniciar un trámite o llenar un formulario requiere enviar datos al servidor (`POST`). Al no haber internet, la solicitud fallará. En implementaciones avanzadas se puede usar "Background Sync" para poner en cola los formularios, pero por defecto, las mutaciones requieren conexión.

#### 2. Editor OnlyOffice
- **No funciona.** OnlyOffice depende de un servidor de documentos independiente que procesa la edición en tiempo real y requiere conexión constante al Document Server mediante websockets y HTTP.

#### 3. El Agente de Inteligencia Artificial (Chat AI)
- **No funciona.** El asistente virtual necesita enviar tus mensajes al servidor, y este servidor a su vez se conecta a modelos de lenguaje (LLMs) como OpenAI, Gemini, etc. Sin internet, esa conexión externa se rompe.

#### 4. Generación de Reportes nuevos
- Si intentas generar un reporte con un rango de fechas nuevo o aplicar filtros que no habías usado antes, fallará, ya que el backend es quien calcula y estructura esa información.

---

## 4. Estado de la implementación técnica
Para cumplir con los requerimientos, se verificó e instaló correctamente el paquete `@angular/pwa`. Esto modificó:
- `angular.json`: Para incluir el `ngsw-config.json` y registrar el Service Worker en la compilación.
- `app.config.ts`: Para proveer el `provideServiceWorker`.
- `ngsw-config.json`: Archivo de configuración donde se establecen los `dataGroups` para cachear llamadas `/api/v1/...`
- `index.html`: Se agregó el `manifest.webmanifest` para los colores del tema, iconos y nombre de la app instalable.
