# wm_backend

> Sistema de Gestión de Trámites y Políticas de Negocio — Backend

Backend del sistema **WorkflowManager**, construido con Spring Boot 3 y MongoDB. Expone una API REST + WebSockets para gestionar políticas de negocio, trámites, usuarios y notificaciones en tiempo real.

---

## Stack

| Tecnología | Versión | Uso |
|------------|---------|-----|
| Java | 17 | Lenguaje principal |
| Spring Boot | 3.2.3 | Framework principal |
| Spring Security | 6.x | Autenticación y autorización |
| Spring Data MongoDB | 4.x | Persistencia de datos |
| Spring WebSocket | 6.x | Comunicación en tiempo real |
| JWT (jjwt) | 0.11.5 | Tokens de autenticación |
| MongoDB | 7.x | Base de datos principal |
| Firebase Admin SDK | 9.2.0 | Push notifications |
| Azure Blob Storage | 12.25.1 | Almacenamiento de archivos |
| Lombok | latest | Reducción de boilerplate |
| Springdoc OpenAPI | 2.3.0 | Documentación Swagger |
| Maven | 3.8+ | Gestión de dependencias |

---

## Requisitos previos

Asegúrate de tener instalado en tu máquina:

```bash
java -version      # Java 17+
mvn -version       # Maven 3.8+
mongod --version   # MongoDB 7.x
git --version      # Git (cualquier versión reciente)
```

---

## Instalación y ejecución local

### 1. Clonar el repositorio

```bash
git clone https://github.com/TU_USUARIO/wm_backend.git
cd wm_backend
```

### 2. Configurar variables de entorno

Crea un archivo `.env` en la raíz del proyecto (nunca se sube al repositorio):

```env
MONGODB_URI=mongodb://localhost:27017/workflow_db
JWT_SECRET=tu-secreto-muy-largo-y-seguro-minimo-32-caracteres
JWT_EXPIRATION=86400000
IA_SERVICE_URL=http://localhost:8001
FIREBASE_CREDENTIALS=
AZURE_STORAGE_CONNECTION_STRING=
```

> Para desarrollo local, solo `MONGODB_URI` y `JWT_SECRET` son obligatorios. El resto puede dejarse vacío hasta que se necesite.

### 3. Iniciar MongoDB local

```bash
# Mac
brew services start mongodb-community@7.0

# Linux
sudo systemctl start mongod

# Windows (si se instaló como servicio)
net start MongoDB
```

### 4. Compilar y ejecutar

```bash
# Compilar
mvn clean compile

# Ejecutar en modo desarrollo
mvn spring-boot:run

# El servidor inicia en: http://localhost:8080
```

### 5. Verificar que funciona

```bash
curl http://localhost:8080/actuator/health
# Respuesta esperada: {"status":"UP"}
```

---

## Documentación de la API

Una vez el servidor esté corriendo, accede a Swagger UI:

```
http://localhost:8080/swagger-ui.html
```

Swagger muestra todos los endpoints disponibles, permite probarlos directamente y documenta los DTOs de request/response.

---

## Estructura del proyecto

```
src/
└── main/
    ├── java/com/workflow/
    │   ├── WorkflowApplication.java     ← Entry point
    │   ├── config/                      ← Configuraciones globales
    │   │   ├── SecurityConfig.java
    │   │   ├── WebSocketConfig.java
    │   │   └── CorsConfig.java
    │   ├── common/                      ← Código compartido
    │   │   ├── dto/
    │   │   │   ├── ApiResponse.java
    │   │   │   └── PageResponse.java
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   └── UnauthorizedException.java
    │   │   └── utils/
    │   │       └── JwtUtil.java
    │   ├── security/
    │   │   ├── JwtFilter.java
    │   │   └── UserDetailsServiceImpl.java
    │   │
    │   ├── auth/                        ← Módulo: Autenticación
    │   │   ├── controller/
    │   │   ├── dto/
    │   │   └── service/
    │   ├── empresa/                     ← Módulo: Empresas
    │   │   ├── controller/
    │   │   ├── dto/
    │   │   ├── model/
    │   │   ├── repository/
    │   │   └── service/
    │   ├── usuario/                     ← Módulo: Usuarios
    │   ├── departamento/                ← Módulo: Departamentos
    │   ├── politica/                    ← Módulo: Políticas de negocio
    │   ├── nodo/                        ← Módulo: Nodos del diagrama
    │   ├── transicion/                  ← Módulo: Transiciones entre nodos
    │   ├── formulario/                  ← Módulo: Formularios dinámicos
    │   ├── tramite/                     ← Módulo: Trámites
    │   ├── ejecucion/                   ← Módulo: Ejecuciones de nodo
    │   └── notificacion/                ← Módulo: Notificaciones
    │
    └── resources/
        └── application.properties
```

Cada módulo es completamente autónomo y contiene sus propias capas:

```
[modulo]/
├── controller/    → Endpoints REST (@RestController)
├── dto/           → Objetos de transferencia (request/response)
├── model/         → Documentos MongoDB (@Document)
├── repository/    → Interfaces MongoRepository
└── service/       → Lógica de negocio (@Service)
```

---

## Base de datos

El proyecto usa **MongoDB** con las siguientes colecciones principales:

| Colección | Descripción |
|-----------|-------------|
| `empresas` | Organizaciones registradas en el sistema |
| `usuarios` | Todos los usuarios (Admin General, Admin Depto, Funcionario) |
| `departamentos` | Áreas o departamentos de cada empresa |
| `politicas` | Políticas de negocio (definición del proceso) |
| `nodos` | Nodos del diagrama de actividades |
| `transiciones` | Flechas entre nodos del diagrama |
| `formularios` | Formularios dinámicos asociados a cada nodo |
| `tramites` | Instancias de procesos en ejecución |
| `ejecuciones_nodo` | Registro de cada paso ejecutado en un trámite |
| `notificaciones` | Notificaciones web y push |
| `analisis_ia` | Resultados de análisis del microservicio IA |

> **No se requieren migraciones.** MongoDB es schemaless. Spring Data MongoDB crea las colecciones automáticamente al guardar el primer documento.

---

## Roles del sistema

| Rol | Descripción | Acceso |
|-----|-------------|--------|
| `ADMIN_GENERAL` | Administrador de la empresa | Todo el sistema |
| `ADMIN_DEPARTAMENTO` | Jefe de un departamento específico | Su departamento |
| `FUNCIONARIO` | Empleado que ejecuta tareas | Sus tareas asignadas |

---

## Variables de entorno

| Variable | Descripción | Requerida en local |
|----------|-------------|-------------------|
| `MONGODB_URI` | URI de conexión a MongoDB | ✅ Sí |
| `JWT_SECRET` | Secreto para firmar tokens JWT (mín. 32 chars) | ✅ Sí |
| `JWT_EXPIRATION` | Expiración del JWT en milisegundos (default: 86400000 = 24h) | No |
| `IA_SERVICE_URL` | URL del microservicio Python | No |
| `FIREBASE_CREDENTIALS` | JSON de credenciales Firebase en base64 | No |
| `AZURE_STORAGE_CONNECTION_STRING` | Conexión a Azure Blob Storage | No |

---

## Despliegue en Azure

El backend se despliega en **Azure App Service** (Java 17, Linux):

```bash
# Build
mvn clean package -DskipTests

# Deploy con Azure CLI
az webapp deploy \
  --resource-group rg-workflow-parcial \
  --name wm-backend \
  --src-path target/workflow-back-0.0.1-SNAPSHOT.jar \
  --type jar
```

**Servicios Azure utilizados:**
- Azure App Service → API principal
- Azure Container Apps → Microservicio IA (Python)
- Azure Blob Storage → Archivos adjuntos de formularios
- MongoDB Atlas → Base de datos en la nube

---

## Convención de commits

Este proyecto sigue [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(auth): implementar autenticación JWT
fix(tramite): corregir motor de workflow en transiciones paralelas
docs(readme): actualizar instrucciones de instalación
refactor(usuario): simplificar validación de roles
test(auth): agregar pruebas unitarias para login
chore(deps): actualizar dependencias de seguridad
```

---

## Licencia

Proyecto académico — Universidad Autónoma Gabriel René Moreno  
Materia: Ingeniería de Software I — Ing. Martínez Canedo
