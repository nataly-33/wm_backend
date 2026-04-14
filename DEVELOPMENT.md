# Guía de Desarrollo — wm_backend

Referencia técnica completa para el desarrollo del backend. Cubre arquitectura, patrones, decisiones técnicas y guías paso a paso para agregar nuevas funcionalidades.

---

## Arquitectura general

```
Cliente (Angular / Flutter)
         │
         │ HTTP REST + WebSocket
         ▼
┌───────────────────────────── ┐
│      Spring Boot API         │
│                              │
│  Controller → Service →      │
│  Repository → MongoDB        │
│                              │
│  Security: JWT Filter        │
│  Tiempo real: WebSocket      │
└───────────────────────────── ┘
         │
         ├── MongoDB (datos)
         ├── Firebase (push)
         └── Azure Blob (archivos)
```

### Capas de la aplicación

| Capa | Clase | Responsabilidad |
|------|-------|-----------------|
| Presentación | `@RestController` | Recibir requests, validar, delegar al service |
| Negocio      | `@Service`        | Toda la lógica de negocio                     |
| Persistencia | `MongoRepository` | Acceso a datos, queries                       |
| Modelo       | `@Document`       | Estructura del documento MongoDB              |

**Regla de oro:** Un controller nunca tiene lógica de negocio. Un repository nunca tiene lógica de negocio. Toda la lógica vive en el service.

---

## Módulos del sistema

| Módulo | Package | Descripción |
|--------|---------|-------------|
| `auth`         | `com.workflow.auth`         | Login, registro, JWT |
| `empresa`      | `com.workflow.empresa`      | CRUD de empresas |
| `usuario`      | `com.workflow.usuario`      | CRUD de usuarios y roles |
| `departamento` | `com.workflow.departamento` | CRUD de departamentos |
| `politica`     | `com.workflow.politica`     | Políticas de negocio |
| `nodo`         | `com.workflow.nodo`         | Nodos del diagrama de actividades |
| `transicion`   | `com.workflow.transicion`   | Conexiones entre nodos |
| `formulario`   | `com.workflow.formulario`   | Formularios dinámicos |
| `tramite`      | `com.workflow.tramite`      | Instancias de procesos |
| `ejecucion`    | `com.workflow.ejecucion`    | Pasos ejecutados de un trámite |
| `notificacion` | `com.workflow.notificacion` | Notificaciones web y push |

---

## Cómo agregar un nuevo módulo

### Paso 1 — Crear la estructura de carpetas

```bash
# Reemplaza [modulo] con el nombre real (ej: pago, reporte)
BASE="src/main/java/com/workflow"
mkdir -p $BASE/[modulo]/controller
mkdir -p $BASE/[modulo]/dto
mkdir -p $BASE/[modulo]/model
mkdir -p $BASE/[modulo]/repository
mkdir -p $BASE/[modulo]/service
```

### Paso 2 — Crear el Model

```java
// src/main/java/com/workflow/[modulo]/model/[Entidad].java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "[entidades]")
public class [Entidad] {
    @Id
    private String id;

    // Campos propios
    private String campo1;
    private Boolean activo;

    // Referencias a otros documentos (solo el ID)
    private String empresaId;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;
}
```

### Paso 3 — Crear el Repository

```java
// src/main/java/com/workflow/[modulo]/repository/[Entidad]Repository.java
@Repository
public interface [Entidad]Repository extends MongoRepository<[Entidad], String> {
    List<[Entidad]> findAllByActivoTrue();
    List<[Entidad]> findAllByEmpresaIdAndActivoTrue(String empresaId);
    Optional<[Entidad]> findByIdAndActivoTrue(String id);
}
```

### Paso 4 — Crear los DTOs

```java
// [Entidad]Request.java
@Data
public class [Entidad]Request {
    @NotBlank(message = "El campo es requerido")
    private String campo1;
}

// [Entidad]Response.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class [Entidad]Response {
    private String id;
    private String campo1;
    private LocalDateTime creadoEn;
}
```

### Paso 5 — Crear el Service

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class [Entidad]Service {
    private final [Entidad]Repository [entidad]Repository;

    public ApiResponse<[Entidad]Response> crear([Entidad]Request request) {
        log.info("Creando [entidad]: {}", request.getCampo1());
        [Entidad] entidad = [Entidad].builder()
                .campo1(request.getCampo1())
                .activo(true)
                .build();
        [Entidad] guardada = [entidad]Repository.save(entidad);
        return ApiResponse.success("[Entidad] creada", mapToResponse(guardada));
    }

    // findAll, findById, update, delete (soft)...

    private [Entidad]Response mapToResponse([Entidad] entidad) {
        return [Entidad]Response.builder()
                .id(entidad.getId())
                .campo1(entidad.getCampo1())
                .creadoEn(entidad.getCreadoEn())
                .build();
    }
}
```

### Paso 6 — Crear el Controller

```java
@Tag(name = "[Entidades]")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/[entidades]")
@RequiredArgsConstructor
public class [Entidad]Controller {
    private final [Entidad]Service [entidad]Service;

    @Operation(summary = "Crear [entidad]")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<[Entidad]Response>> crear(
            @Valid @RequestBody [Entidad]Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body([entidad]Service.crear(request));
    }

    // GET, GET /{id}, PUT /{id}, DELETE /{id}...
}
```

---

## Clase ApiResponse — Respuesta estándar

Todo endpoint retorna `ApiResponse<T>`. Su estructura:

```json
{
  "status": 200,
  "message": "Operación exitosa",
  "data": { ... }
}
```

```java
// Uso en services
return ApiResponse.success("Empresa creada", empresaResponse);
return ApiResponse.error("No encontrado");

// Uso en controllers
return ResponseEntity.ok(service.metodo());
return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
```

---

## Seguridad y JWT

### Cómo funciona el flujo de autenticación

```
1. POST /api/v1/auth/login con { email, password }
2. AuthService verifica credenciales con bcrypt
3. Si son correctas, genera JWT con { userId, email, rol, empresaId }
4. Cliente guarda el JWT
5. En cada request, JwtFilter intercepta y valida el token
6. Si el token es válido, Spring Security carga el usuario
7. @PreAuthorize verifica el rol
```

### Acceder al usuario autenticado en un service

```java
// Inyectar en el service cuando necesitas saber quién está logueado
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String userId = (String) auth.getPrincipal(); // El userId está en el principal
```

### Roles disponibles

```java
// Usar siempre estas constantes, nunca strings hardcodeados
@PreAuthorize("hasRole('ADMIN_GENERAL')")
@PreAuthorize("hasRole('ADMIN_DEPARTAMENTO')")
@PreAuthorize("hasRole('FUNCIONARIO')")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_DEPARTAMENTO')")
```

---

## WebSockets

### Canales disponibles

| Canal | Descripción | Quién escucha |
|-------|-------------|---------------|
| `/topic/politica/{politicaId}` | Cambios en el monitor de una política | Admin General, Admin Depto |
| `/topic/usuario/{usuarioId}` | Notificaciones personales | Cualquier usuario |

### Emitir un evento desde un service

```java
@Service
@RequiredArgsConstructor
public class TramiteService {

    private final SimpMessagingTemplate messagingTemplate;

    private void emitirCambioEnMonitor(String politicaId, Object payload) {
        messagingTemplate.convertAndSend(
            "/topic/politica/" + politicaId,
            payload
        );
    }

    private void emitirNotificacionUsuario(String usuarioId, Object payload) {
        messagingTemplate.convertAndSend(
            "/topic/usuario/" + usuarioId,
            payload
        );
    }
}
```

---

## Manejo de errores

### Excepciones personalizadas disponibles

| Excepción | HTTP Status | Cuándo usar |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 | Recurso no encontrado |
| `UnauthorizedException` | 401 | Sin permiso para la operación |
| `BadRequestException` | 400 | Datos incorrectos o lógica de negocio inválida |

### Cómo lanzar excepciones

```java
// En cualquier service
throw new ResourceNotFoundException("Usuario no encontrado con id: " + id);
throw new UnauthorizedException("No tienes permiso para modificar este formulario");
throw new BadRequestException("La política ya está activa. Desactívala primero");
```

El `GlobalExceptionHandler` las captura automáticamente y retorna:

```json
{
  "status": 404,
  "message": "Usuario no encontrado con id: abc123",
  "data": null
}
```

---

## MongoDB — Queries avanzadas

### Query por múltiples condiciones

```java
// En el repository
@Query("{ 'empresaId': ?0, 'activo': true, 'rol': ?1 }")
List<Usuario> findByEmpresaAndRol(String empresaId, String rol);
```

### Query con MongoTemplate (para casos complejos)

```java
@Autowired
private MongoTemplate mongoTemplate;

public List<Ejecucion> findByTramiteAndDepartamento(String tramiteId, String deptoId) {
    Query query = new Query();
    query.addCriteria(Criteria.where("tramiteId").is(tramiteId)
            .and("departamentoId").is(deptoId)
            .and("estado").ne("COMPLETADO"));
    return mongoTemplate.find(query, Ejecucion.class);
}
```

### Paginación

```java
public Page<Tramite> listarPaginado(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("creadoEn").descending());
    return tramiteRepository.findAllByActivoTrue(pageable);
}
```

---

## Configuración de CORS

El CORS se configura en `CorsConfig.java`. Para desarrollo local, acepta `http://localhost:4200` (Angular). Para producción, el dominio de Azure Static Web Apps.

Si necesitas agregar un nuevo origen permitido, modifica `CorsConfig.java`:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "http://localhost:4200",
        "https://tu-app.azurestaticapps.net"  // Agregar aquí
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    // ...
}
```

---

## Comandos de referencia rápida

```bash
# Compilar
mvn clean compile

# Ejecutar
mvn spring-boot:run

# Tests
mvn test

# Build para producción (genera el JAR)
mvn clean package -DskipTests

# Ver dependencias del proyecto
mvn dependency:tree

# Verificar versiones desactualizadas
mvn versions:display-dependency-updates

# Limpiar cache de Maven
mvn dependency:purge-local-repository
```

---

## Variables de entorno para desarrollo local

Crea el archivo `.env` en la raíz del proyecto (ya está en `.gitignore`):

```env
MONGODB_URI=mongodb://localhost:27017/workflow_db
JWT_SECRET=local-dev-secret-muy-largo-minimo-32-caracteres-2024
JWT_EXPIRATION=86400000
IA_SERVICE_URL=http://localhost:8001
FIREBASE_CREDENTIALS=
AZURE_STORAGE_CONNECTION_STRING=
```

Para cargar el `.env` automáticamente al correr con Maven, agrega en `application.properties`:

```properties
spring.config.import=optional:file:.env[.properties]
```

O en su defecto, el IDE (IntelliJ / VS Code) permite configurar variables de entorno en la configuración de ejecución.

---

## Herramientas recomendadas

| Herramienta | Uso |
|-------------|-----|
| **IntelliJ IDEA** | IDE principal (Community o Ultimate) |
| **MongoDB Compass** | GUI para ver y editar datos en MongoDB |
| **Postman** | Probar endpoints durante desarrollo |
| **VS Code** | Alternativa si ya tienes configurado todo |
| **Git** | Control de versiones |

### Plugins de IntelliJ recomendados

- **Lombok Plugin** — para que IntelliJ entienda las anotaciones de Lombok
- **MongoDB Plugin** — conexión a MongoDB desde el IDE
- **SonarLint** — análisis de calidad de código en tiempo real
- **GitToolBox** — mejor integración con Git

---

## Glosario del proyecto

| Término | Significado |
|---------|-------------|
| Política de negocio | El proceso completo que define el flujo de un trámite |
| Nodo | Una caja en el diagrama (inicio, tarea, decisión, fin) |
| Transición | Una flecha entre dos nodos |
| Trámite | Una instancia concreta corriendo sobre una política |
| Ejecución de nodo | El trabajo que hace un funcionario en un nodo específico del trámite |
| Motor de workflow | El servicio que mueve automáticamente el trámite al siguiente nodo |
| Carril (swimlane) | La calle del diagrama asignada a un departamento |
