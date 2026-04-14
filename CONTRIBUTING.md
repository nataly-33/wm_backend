# Guía de Contribución — wm_backend

Esta guía define las reglas y convenciones para contribuir al backend del proyecto WorkflowManager. Seguirla garantiza un código limpio, consistente y fácil de mantener.

---

## Flujo de trabajo con Git

### Rama principal

Este proyecto trabaja sobre **una sola rama: `main`**.

- `main` es la rama de producción y desarrollo al mismo tiempo
- **Nunca** hagas push directamente a `main` con código sin probar
- Cada commit debe dejar el proyecto en estado funcional (el build no puede romperse)

### Antes de hacer commit

```bash
# 1. Asegúrate de que el proyecto compila
mvn clean compile

# 2. Asegúrate de que los tests pasan
mvn test

# 3. Recién entonces, commitear
git add .
git commit -m "feat(modulo): descripción del cambio"
git push origin main
```

---

## Convención de commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/). **Todos los commits deben seguir este formato sin excepción.**

### Formato

```
<tipo>(<ámbito>): <descripción corta en minúsculas>

[cuerpo opcional — explicar el QUÉ y el POR QUÉ]

[footer opcional — referencias a issues]
```

### Tipos permitidos

| Tipo | Cuándo usarlo |
|------|---------------|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de un bug |
| `docs` | Solo documentación |
| `style` | Formato, espacios, punto y coma (sin cambio de lógica) |
| `refactor` | Refactorización sin nueva funcionalidad ni bug fix |
| `perf` | Mejora de rendimiento |
| `test` | Agregar o corregir tests |
| `chore` | Tareas de mantenimiento, dependencias, configuración |
| `ci` | Cambios en CI/CD |
| `build` | Cambios en el sistema de build |

### Ámbitos del proyecto

Usa siempre el nombre del módulo como ámbito:

```
auth, empresa, usuario, departamento, politica,
nodo, transicion, formulario, tramite, ejecucion,
notificacion, config, security, common
```

### Ejemplos correctos

```bash
# Nueva funcionalidad
git commit -m "feat(auth): implementar login con JWT y bcrypt"
git commit -m "feat(tramite): agregar motor de workflow para transiciones lineales"
git commit -m "feat(notificacion): integrar Firebase para push notifications"

# Corrección de bugs
git commit -m "fix(tramite): corregir evaluación de condiciones en transición ALTERNATIVA"
git commit -m "fix(auth): resolver error 401 en rutas con WebSocket"

# Documentación
git commit -m "docs(readme): agregar instrucciones de instalación en Linux"
git commit -m "docs(swagger): documentar endpoints del módulo de formularios"

# Refactoring
git commit -m "refactor(usuario): extraer validación de roles a clase utilitaria"
git commit -m "refactor(common): unificar estructura de ApiResponse"

# Configuración / mantenimiento
git commit -m "chore(deps): actualizar jjwt a versión 0.11.5"
git commit -m "chore(config): agregar variable de entorno para Azure Blob Storage"

# Tests
git commit -m "test(auth): agregar tests unitarios para AuthService"
```

### Ejemplos incorrectos

```bash
# ❌ Sin tipo
git commit -m "arreglé el login"

# ❌ Sin ámbito
git commit -m "feat: nueva funcionalidad"

# ❌ Descripción en mayúscula
git commit -m "feat(auth): Implementar Login"

# ❌ Demasiado genérico
git commit -m "fix: varios arreglos"

# ❌ En inglés mezclado sin consistencia
git commit -m "feat(auth): add JWT y arreglo del login"
```

---

## Proceso de desarrollo por módulo

### Orden obligatorio al crear una nueva funcionalidad

Siempre en este orden — nunca saltarse pasos:

```
1. Model      → Documento MongoDB (@Document)
2. Repository → Interfaz MongoRepository
3. DTO        → Clases de request y response
4. Service    → Lógica de negocio (@Service)
5. Controller → Endpoints REST (@RestController)
```

### 1. Model — Documento MongoDB

**Ubicación:** `src/main/java/com/workflow/[modulo]/model/[Entidad].java`

```java
package com.workflow.empresa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data                    // Lombok: getters, setters, equals, hashCode, toString
@Builder                 // Lombok: patrón builder
@NoArgsConstructor       // Lombok: constructor vacío
@AllArgsConstructor      // Lombok: constructor con todos los campos
@Document(collection = "empresas")  // Nombre de la colección en MongoDB
public class Empresa {

    @Id
    private String id;   // MongoDB usa String para los ObjectId

    private String nombre;
    private String logoUrl;
    private Boolean activo;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;
}
```

**Reglas:**
- Siempre usar `String` para el `@Id` (MongoDB ObjectId se serializa como String)
- Usar `@Document(collection = "nombre_en_snake_case")`
- Incluir siempre `creadoEn` y `actualizadoEn` con las anotaciones de auditoría
- Usar Lombok siempre: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Los campos de referencia a otros documentos se guardan como `String` (el ID)

### 2. Repository — MongoRepository

**Ubicación:** `src/main/java/com/workflow/[modulo]/repository/[Entidad]Repository.java`

```java
package com.workflow.empresa.repository;

import com.workflow.empresa.model.Empresa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpresaRepository extends MongoRepository<Empresa, String> {

    // Spring Data genera la query automáticamente por el nombre del método
    List<Empresa> findAllByActivoTrue();
    Optional<Empresa> findByIdAndActivoTrue(String id);
    Optional<Empresa> findByNombreIgnoreCase(String nombre);
    boolean existsByNombre(String nombre);
}
```

**Reglas:**
- Siempre `extends MongoRepository<Entidad, String>`
- Usar convención de nombres de Spring Data: `findBy`, `existsBy`, `deleteBy`, etc.
- Anotar con `@Repository`
- No escribir queries manuales si Spring Data puede generarlas por el nombre del método

### 3. DTOs — Request y Response

**Ubicación:** `src/main/java/com/workflow/[modulo]/dto/`

```java
// EmpresaRequest.java — Lo que recibe el endpoint
package com.workflow.empresa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmpresaRequest {

    @NotBlank(message = "El nombre es requerido")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String nombre;

    private String logoUrl;
}
```

```java
// EmpresaResponse.java — Lo que retorna el endpoint
package com.workflow.empresa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaResponse {
    private String id;
    private String nombre;
    private String logoUrl;
    private Boolean activo;
    private LocalDateTime creadoEn;
}
```

**Reglas:**
- **Request**: usar `@NotBlank`, `@NotNull`, `@Size`, `@Email` para validación
- **Response**: solo los campos que el cliente necesita ver (nunca exponer passwords)
- Un DTO por operación si las validaciones difieren mucho (CreateRequest vs UpdateRequest)
- Siempre Lombok en los DTOs

### 4. Service — Lógica de negocio

**Ubicación:** `src/main/java/com/workflow/[modulo]/service/[Entidad]Service.java`

```java
package com.workflow.empresa.service;

import com.workflow.common.dto.ApiResponse;
import com.workflow.common.exception.ResourceNotFoundException;
import com.workflow.empresa.dto.EmpresaRequest;
import com.workflow.empresa.dto.EmpresaResponse;
import com.workflow.empresa.model.Empresa;
import com.workflow.empresa.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j                   // Lombok: logger → log.info(), log.error(), etc.
@Service
@RequiredArgsConstructor // Lombok: inyección por constructor (reemplaza @Autowired)
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    public ApiResponse<EmpresaResponse> crear(EmpresaRequest request) {
        log.info("Creando empresa: {}", request.getNombre());

        Empresa empresa = Empresa.builder()
                .nombre(request.getNombre())
                .logoUrl(request.getLogoUrl())
                .activo(true)
                .build();

        Empresa guardada = empresaRepository.save(empresa);
        log.info("Empresa creada con id: {}", guardada.getId());

        return ApiResponse.success("Empresa creada exitosamente", mapToResponse(guardada));
    }

    public ApiResponse<List<EmpresaResponse>> listarTodas() {
        List<EmpresaResponse> empresas = empresaRepository.findAllByActivoTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return ApiResponse.success("Empresas obtenidas", empresas);
    }

    public ApiResponse<EmpresaResponse> obtenerPorId(String id) {
        Empresa empresa = empresaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada con id: " + id));

        return ApiResponse.success("Empresa obtenida", mapToResponse(empresa));
    }

    public ApiResponse<EmpresaResponse> actualizar(String id, EmpresaRequest request) {
        Empresa empresa = empresaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada con id: " + id));

        empresa.setNombre(request.getNombre());
        if (request.getLogoUrl() != null) {
            empresa.setLogoUrl(request.getLogoUrl());
        }

        Empresa actualizada = empresaRepository.save(empresa);
        return ApiResponse.success("Empresa actualizada", mapToResponse(actualizada));
    }

    public ApiResponse<Void> eliminar(String id) {
        Empresa empresa = empresaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada con id: " + id));

        empresa.setActivo(false);  // Soft delete
        empresaRepository.save(empresa);

        return ApiResponse.success("Empresa eliminada", null);
    }

    // Mapper privado — evitar dependencias externas para operaciones simples
    private EmpresaResponse mapToResponse(Empresa empresa) {
        return EmpresaResponse.builder()
                .id(empresa.getId())
                .nombre(empresa.getNombre())
                .logoUrl(empresa.getLogoUrl())
                .activo(empresa.getActivo())
                .creadoEn(empresa.getCreadoEn())
                .build();
    }
}
```

**Reglas:**
- Siempre `@Slf4j` para logging. Usar `log.info()` en operaciones importantes, `log.error()` en excepciones
- Siempre `@RequiredArgsConstructor` con campos `final` para inyección de dependencias
- Nunca `@Autowired` en campos (usar inyección por constructor)
- Siempre retornar `ApiResponse<T>` como respuesta estándar
- Soft delete: nunca `delete()` del repositorio, siempre `activo = false`
- Mappers privados dentro del mismo servicio (para casos simples)
- Lanzar siempre `ResourceNotFoundException` cuando no se encuentre un recurso

### 5. Controller — Endpoints REST

**Ubicación:** `src/main/java/com/workflow/[modulo]/controller/[Entidad]Controller.java`

```java
package com.workflow.empresa.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.empresa.dto.EmpresaRequest;
import com.workflow.empresa.dto.EmpresaResponse;
import com.workflow.empresa.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Empresas", description = "Gestión de empresas registradas en el sistema")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @Operation(summary = "Crear empresa", description = "Registra una nueva empresa en el sistema")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<EmpresaResponse>> crear(
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresaService.crear(request));
    }

    @Operation(summary = "Listar empresas")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<List<EmpresaResponse>>> listarTodas() {
        return ResponseEntity.ok(empresaService.listarTodas());
    }

    @Operation(summary = "Obtener empresa por ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<EmpresaResponse>> obtenerPorId(
            @PathVariable String id) {
        return ResponseEntity.ok(empresaService.obtenerPorId(id));
    }

    @Operation(summary = "Actualizar empresa")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<EmpresaResponse>> actualizar(
            @PathVariable String id,
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(empresaService.actualizar(id, request));
    }

    @Operation(summary = "Eliminar empresa (soft delete)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable String id) {
        return ResponseEntity.ok(empresaService.eliminar(id));
    }
}
```

**Reglas:**
- Siempre `@Tag` y `@Operation` de Swagger para documentación
- Siempre `@SecurityRequirement(name = "bearerAuth")` si el endpoint requiere token
- Siempre `@PreAuthorize("hasRole('ROL')")` para control de acceso
- Siempre `@Valid` en `@RequestBody` para activar validaciones del DTO
- Prefijo de rutas: `/api/v1/[modulo-en-plural]`
- Usar los status HTTP correctos: 201 para creación, 200 para el resto, 204 para delete sin body

---

## Respuesta estándar de la API

**Todas** las respuestas deben usar `ApiResponse<T>`:

```java
// Éxito con datos
ApiResponse.success("Mensaje", data);
// → { "status": 200, "message": "Mensaje", "data": {...} }

// Error
ApiResponse.error("Mensaje de error");
// → { "status": 400, "message": "Mensaje de error", "data": null }
```

---

## Manejo de errores

- Nunca capturar excepciones en los controllers
- Lanzar siempre excepciones específicas desde los services
- El `GlobalExceptionHandler` en `common/exception/` las captura y formatea
- Excepciones disponibles:
  - `ResourceNotFoundException` → 404
  - `UnauthorizedException` → 401
  - `BadRequestException` → 400

---

## Checklist antes de hacer commit

```
□ El proyecto compila sin errores (mvn clean compile)
□ Los tests pasan (mvn test)
□ El nuevo endpoint aparece en Swagger
□ El endpoint tiene @PreAuthorize con el rol correcto
□ Los campos del DTO tienen validaciones (@NotBlank, @Size, etc.)
□ El servicio usa log.info() en operaciones importantes
□ El delete es soft delete (activo = false), nunca físico
□ El commit sigue la convención de Conventional Commits
□ No se subieron archivos de credenciales (.env, firebase-credentials.json)
```
