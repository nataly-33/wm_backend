package com.workflow.documento.controller;

import com.workflow.documento.dto.DocumentoRequest;
import com.workflow.documento.dto.DocumentoResponse;
import com.workflow.documento.model.AuditoriaDocumento;
import com.workflow.documento.model.DocumentoTramite;
import com.workflow.documento.model.PermisosDocumento;
import com.workflow.documento.repository.DocumentoRepository;
import com.workflow.documento.repository.DocumentoTramiteRepository;
import com.workflow.documento.service.DocumentoService;
import com.workflow.documento.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.workflow.usuario.repository.UsuarioRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;
    private final DocumentoRepository documentoRepo;
    private final DocumentoTramiteRepository documentoTramiteRepo;
    private final S3Service s3Service;
    private final UsuarioRepository usuarioRepository;

    @Value("${onlyoffice.jwt.secret:workflow-onlyoffice-secret-key-para-evitar-el-error-de-256-bits}")
    private String onlyofficeJwtSecret;

    @Value("${backend.internal.url:http://wm-backend:8080}")
    private String backendInternalUrl;

    private static final String S3_DYN_PREFIX = "s3_dyn_";

    // ── Documentos (colección principal) ────────────────────────────────────

    @PostMapping("/api/v1/documentos/upload")
    public ResponseEntity<DocumentoResponse> subir(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart("datos") DocumentoRequest request,
            java.security.Principal principal) throws IOException {
        String userId = principal.getName();
        return ResponseEntity.ok(documentoService.subirDocumento(archivo, request, userId, userId));
    }

    @GetMapping("/api/v1/documentos")
    public ResponseEntity<List<DocumentoResponse>> listar(
            @RequestParam String empresaId,
            @RequestParam(required = false) String carpetaId,
            @RequestParam(required = false) String politicaId,
            @RequestParam(required = false) String tramiteId) {
        return ResponseEntity.ok(documentoService.listar(empresaId, carpetaId, politicaId, tramiteId));
    }

    /**
     * Retorna todos los documentos de oficina (creados desde el editor).
     * GET /api/v1/documentos/oficina
     */
    @GetMapping("/api/v1/documentos/oficina")
    public ResponseEntity<List<DocumentoResponse>> listarOficina() {
        return ResponseEntity.ok(documentoService.listarDocsOficina());
    }

    @PostMapping("/api/v1/documentos/{id}/version")
    public ResponseEntity<DocumentoResponse> nuevaVersion(
            @PathVariable String id,
            @RequestPart("archivo") MultipartFile archivo,
            java.security.Principal principal) throws IOException {
        String userId = principal.getName();
        return ResponseEntity.ok(documentoService.subirNuevaVersion(id, archivo, userId, userId));
    }

    @GetMapping("/api/v1/documentos/{id}/auditoria")
    public ResponseEntity<List<AuditoriaDocumento>> auditoria(@PathVariable String id) {
        return ResponseEntity.ok(documentoService.obtenerAuditoria(id));
    }

    @PutMapping("/api/v1/documentos/{id}/permisos")
    public ResponseEntity<DocumentoResponse> cambiarPermisos(
            @PathVariable String id,
            @RequestBody PermisosDocumento permisos,
            java.security.Principal principal) {
        return ResponseEntity.ok(documentoService.cambiarPermisos(id, permisos, principal.getName()));
    }

    @GetMapping("/api/v1/documentos/{id}/onlyoffice-config")
    public ResponseEntity<Map<String, Object>> onlyOfficeConfig(
            @PathVariable String id,
            @RequestParam(defaultValue = "view") String modo,
            java.security.Principal principal) {
        String userId = principal.getName();
        String userName = usuarioRepository.findById(userId)
            .map(u -> {
                String n = u.getNombre() != null ? u.getNombre() : "";
                return n.isBlank() ? userId : n;
            }).orElse(userId);
        return ResponseEntity.ok(documentoService.generarConfigOnlyOffice(id, userId, userName, modo));
    }
    
    @DeleteMapping("/api/v1/documentos/{id}")
    public ResponseEntity<Void> eliminarDocumento(
            @PathVariable String id,
            java.security.Principal principal) {
        documentoService.eliminarDocumento(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // ── Repositorio de archivos de trámite ───────────────────────────────────

    /** Retorna todos los archivos del cliente agrupados por trámite y departamento. */
    @GetMapping("/api/v1/clientes/{clienteId}/repositorio")
    public ResponseEntity<Map<String, Object>> repositorioCliente(
            @PathVariable String clienteId) {

        List<DocumentoTramite> docs = documentoTramiteRepo.findByClienteId(clienteId);

        Map<String, Map<String, List<DocumentoTramite>>> agrupado = docs.stream()
            .collect(Collectors.groupingBy(
                DocumentoTramite::getTramiteId,
                Collectors.groupingBy(DocumentoTramite::getDepartamentoNombre)
            ));

        return ResponseEntity.ok(Map.of("clienteId", clienteId, "repositorio", agrupado));
    }

    /** Retorna archivos de un trámite específico desde S3 agrupados por departamento. */
    @GetMapping("/api/v1/tramites/{tramiteId}/archivos-s3")
    public ResponseEntity<Map<String, List<String>>> archivosS3Tramite(
            @PathVariable String tramiteId,
            @RequestParam String empresaId,
            @RequestParam String clienteId) {

        return ResponseEntity.ok(
            s3Service.listarArchivosTramite(empresaId, clienteId, tramiteId)
        );
    }

    /**
     * Genera una URL pre-firmada de S3 válida por 60 minutos a partir de una s3Key.
     * Usar este endpoint para visualizar o descargar archivos en lugar de acceder
     * directamente a la URL pública (que puede ser inaccesible si el bucket es privado).
     *
     * GET /api/v1/documentos/presignado?key=empresaId/clienteId/tramiteId/depto/timestamp_archivo
     */
    @GetMapping("/api/v1/documentos/presignado")
    public ResponseEntity<Map<String, String>> urlPresignada(
            @RequestParam String key,
            @RequestParam(defaultValue = "60") int minutos) {
        String url = s3Service.generarUrlPresignada(key, minutos);
        return ResponseEntity.ok(Map.of("url", url, "key", key, "expiraEnMinutos", String.valueOf(minutos)));
    }

    /**
     * Genera URL presignada a partir de la URL pública almacenada en MongoDB.
     * Útil cuando sólo se tiene la urlArchivo del DocumentoResponse.
     *
     * GET /api/v1/documentos/presignado-por-url?url=https://bucket.s3.amazonaws.com/...
     */
    @GetMapping("/api/v1/documentos/presignado-por-url")
    public ResponseEntity<Map<String, String>> urlPresignadaPorUrl(
            @RequestParam String url,
            @RequestParam(defaultValue = "60") int minutos) {
        String key = s3Service.extraerKeyDeUrl(url);
        String urlFirmada = s3Service.generarUrlPresignada(key, minutos);
        return ResponseEntity.ok(Map.of("url", urlFirmada, "key", key, "expiraEnMinutos", String.valueOf(minutos)));
    }

    /**
     * Busca un documento por su URL almacenada en MongoDB.
     * Primero busca en la colección principal Documento, luego en DocumentoTramite.
     * Cuando se encuentra en DocumentoTramite retorna esTramite=true y la urlPresignada
     * para que el frontend pueda armar el config de OnlyOffice sin una segunda llamada.
     *
     * GET /api/v1/documentos/buscar-por-url?url=https://...
     */
    @GetMapping("/api/v1/documentos/buscar-por-url")
    public ResponseEntity<?> buscarPorUrl(@RequestParam String url) {
        // Primero buscar en la colección principal Documento
        Optional<?> docOpt = documentoService.buscarPorUrl(url)
            .map(doc -> (Object) Map.of(
                "id", doc.getId(),
                "nombre", doc.getNombre(),
                "tipoDocumento", doc.getTipoDocumento() != null ? doc.getTipoDocumento() : "",
                "esDocumentoOficina", doc.isEsDocumentoOficina(),
                "esTramite", false
            ));
        if (docOpt.isPresent()) {
            return ResponseEntity.ok(docOpt.get());
        }
        // Si no está en Documento, buscar en DocumentoTramite usando query indexada (no findAll)
        Optional<?> dtOpt = documentoTramiteRepo.findByUrlS3(url)
            .map(dt -> {
                String key = dt.getS3Key() != null && !dt.getS3Key().isBlank()
                    ? dt.getS3Key()
                    : s3Service.extraerKeyDeUrl(dt.getUrlS3());
                String presignedUrl = s3Service.generarUrlPresignada(key, 1440);
                String ext = dt.getNombreArchivo() != null && dt.getNombreArchivo().contains(".")
                    ? dt.getNombreArchivo().substring(dt.getNombreArchivo().lastIndexOf('.') + 1).toLowerCase()
                    : "docx";
                return (Object) Map.of(
                    "id", dt.getId(),
                    "nombre", dt.getNombreArchivo() != null ? dt.getNombreArchivo() : "documento",
                    "tipoDocumento", ext,
                    "esDocumentoOficina", false,
                    "urlPresignada", presignedUrl,
                    "esTramite", true
                );
            });
            
        if (dtOpt.isPresent()) {
            return ResponseEntity.ok(dtOpt.get());
        }

        // Fallback: si no está en BD pero es una URL de S3 (como archivos de respuestas)
        String key = s3Service.extraerKeyDeUrl(url);
        if (key != null && !key.isBlank()) {
            String presignedUrl = s3Service.generarUrlPresignada(key, 1440);
            String nombreArchivo = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
            if (nombreArchivo.matches("\\d+_.+")) {
                nombreArchivo = nombreArchivo.replaceFirst("^\\d+_", "");
            }
            String ext = nombreArchivo.contains(".")
                ? nombreArchivo.substring(nombreArchivo.lastIndexOf('.') + 1).toLowerCase()
                : "docx";
            String base64Key = java.util.Base64.getUrlEncoder().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok(Map.of(
                "id", S3_DYN_PREFIX + base64Key,
                "nombre", nombreArchivo,
                "tipoDocumento", ext,
                "esDocumentoOficina", false,
                "urlPresignada", presignedUrl,
                "esTramite", true
            ));
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Genera la configuración de OnlyOffice para un documento de trámite (DocumentoTramite)
     * a partir de su URL pública de S3. No requiere que el documento esté en la colección Documento.
     * OnlyOffice leerá el archivo con la URL pre-firmada generada en este endpoint.
     *
     * GET /api/v1/onlyoffice/config-tramite?url=https://wm-documentos.s3.amazonaws.com/...
     */
    @GetMapping("/api/v1/onlyoffice/config-tramite")
    public ResponseEntity<?> onlyOfficeConfigTramite(
            @RequestParam String url,
            @RequestParam(defaultValue = "edit") String modo,
            java.security.Principal principal) {
        String userId = principal != null ? principal.getName() : "anonimo";
        // Extraer la key de S3 (quita parámetros de firma si los hubiera)
        String key = s3Service.extraerKeyDeUrl(url);
        if (key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo extraer la key de S3 de la URL proporcionada"));
        }
        // Determinar nombre y tipo de archivo desde la key
        String nombreArchivo = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
        // Quitar timestamp si tiene formato {timestamp}_{nombre}
        if (nombreArchivo.matches("\\d+_.+")) {
            nombreArchivo = nombreArchivo.replaceFirst("^\\d+_", "");
        }
        String ext = nombreArchivo.contains(".")
            ? nombreArchivo.substring(nombreArchivo.lastIndexOf('.') + 1).toLowerCase()
            : "docx";
        String docType = switch (ext) {
            case "xlsx", "xls", "ods", "csv" -> "cell";
            case "pptx", "ppt", "odp"        -> "slide";
            default                           -> "word";
        };
        // Buscar en DocumentoTramite para obtener metadatos adicionales si está disponible
        Optional<com.workflow.documento.model.DocumentoTramite> dtOpt = documentoTramiteRepo.findByUrlS3(url);
        String docId = dtOpt
            .map(dt -> dt.getId())
            .orElseGet(() -> S3_DYN_PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Incluir versión en la clave para que OnlyOffice recargue desde S3 al cambiar la versión
        int docVersion = dtOpt.map(dt -> dt.getVersion() != null ? dt.getVersion() : 1)
            .orElseGet(() -> documentoService.getS3DynVersion(docId));
        String docKey = docId + "_v" + docVersion;

        String callbackUrl = documentoService.getOnlyofficeCallbackUrl() + docId;
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("scriptUrl", documentoService.getOnlyofficeScriptUrl());
        config.put("documentType", docType);
        // Usar proxy interno: OnlyOffice (Docker) descarga desde wm-backend, no directamente desde S3
        String proxyDocUrl = backendInternalUrl + "/api/v1/documents/proxy/tramite/" + docId;
        config.put("document", Map.of(
            "fileType", ext,
            "key",      docKey,
            "title",    nombreArchivo,
            "url",      proxyDocUrl
        ));
        String userName = usuarioRepository.findById(userId)
            .map(u -> {
                String n = u.getNombre() != null ? u.getNombre() : "";
                return n.isBlank() ? userId : n;
            }).orElse(userId);
        
        config.put("editorConfig", Map.of(
            "callbackUrl", callbackUrl,
            "mode", modo,
            "user", Map.of("id", userId, "name", userName),
            "lang", "es",
            "coEditing", Map.of("mode", "fast", "change", true)
        ));

        if (onlyofficeJwtSecret != null && !onlyofficeJwtSecret.isBlank()) {
            try {
                String token = io.jsonwebtoken.Jwts.builder()
                        .setClaims(config)
                        .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .compact();
                config.put("token", token);
            } catch (Exception e) {
                // Loguear el error silenciosamente
            }
        }

        return ResponseEntity.ok(config);
    }

    /**
     * Crea un documento de oficina en blanco (docx, xlsx, pptx, odt, ods, odp, csv, txt)
     * y lo sube a S3. El documento se abre completamente en blanco en OnlyOffice.
     *
     * POST /api/v1/documentos/crear-oficina
     * Body: { "nombre": "Mi documento", "tipo": "docx" }
     */
    @PostMapping("/api/v1/documentos/crear-oficina")
    public ResponseEntity<DocumentoResponse> crearDocumentoOficina(
            @RequestBody Map<String, String> body,
            java.security.Principal principal) throws IOException {
        String nombre = body.getOrDefault("nombre", "Nuevo documento");
        String tipo   = body.getOrDefault("tipo",   "docx");
        String userId = principal.getName();
        var doc = documentoService.crearDocumentoOficina(nombre, tipo, userId);
        DocumentoResponse resp = DocumentoResponse.builder()
            .id(doc.getId()).nombre(doc.getNombre()).tipoMime(doc.getTipoMime())
            .urlArchivo(doc.getUrlArchivo()).s3Key(doc.getS3Key())
            .tamanioBytes(doc.getTamanioBytes()).version(doc.getVersion())
            .creadoPorId(doc.getCreadoPorId()).creadoPorNombre(doc.getCreadoPorNombre())
            .creadoEn(doc.getCreadoEn()).modificadoEn(doc.getModificadoEn())
            .esDocumentoOficina(doc.isEsDocumentoOficina())
            .tipoDocumento(doc.getTipoDocumento())
            .build();
        return ResponseEntity.status(201).body(resp);
    }

    /**
     * Proxy interno para que OnlyOffice descargue documentos desde wm-backend (Docker network)
     * en lugar de hacerlo directamente desde S3 con URL presignada (que devuelve 400).
     *
     * GET /api/v1/documents/proxy/tramite/{docId}
     */
    @GetMapping("/api/v1/documents/proxy/tramite/{docId}")
    public ResponseEntity<byte[]> proxyDocumentoTramite(@PathVariable String docId) {
        String s3Key;
        if (docId.startsWith(S3_DYN_PREFIX)) {
            String encoded = docId.substring(S3_DYN_PREFIX.length());
            s3Key = new String(java.util.Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            // Buscar primero en DocumentoTramite, luego en Documento (oficina)
            com.workflow.documento.model.DocumentoTramite dt = documentoTramiteRepo.findById(docId).orElse(null);
            if (dt != null) {
                s3Key = s3Service.extraerKeyDeUrl(dt.getUrlS3());
            } else {
                com.workflow.documento.model.Documento doc = documentoRepo.findById(docId).orElse(null);
                if (doc == null || doc.getUrlArchivo() == null) return ResponseEntity.notFound().build();
                s3Key = doc.getS3Key() != null ? doc.getS3Key() : s3Service.extraerKeyDeUrl(doc.getUrlArchivo());
            }
        }
        byte[] content = s3Service.descargarBytes(s3Key);
        if (content == null) return ResponseEntity.notFound().build();
        String ext = s3Key.contains(".") ? s3Key.substring(s3Key.lastIndexOf('.') + 1).toLowerCase() : "bin";
        String contentType = switch (ext) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odt"  -> "application/vnd.oasis.opendocument.text";
            case "ods"  -> "application/vnd.oasis.opendocument.spreadsheet";
            case "pdf"  -> "application/pdf";
            default     -> "application/octet-stream";
        };
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"document." + ext + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }
}
