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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
        return ResponseEntity.ok(documentoService.generarConfigOnlyOffice(id, userId, userId, modo));
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
        return documentoTramiteRepo.findByUrlS3(url)
            .map(dt -> {
                String key = dt.getS3Key() != null && !dt.getS3Key().isBlank()
                    ? dt.getS3Key()
                    : s3Service.extraerKeyDeUrl(dt.getUrlS3());
                String presignedUrl = s3Service.generarUrlPresignada(key, 1440);
                String ext = dt.getNombreArchivo() != null && dt.getNombreArchivo().contains(".")
                    ? dt.getNombreArchivo().substring(dt.getNombreArchivo().lastIndexOf('.') + 1).toLowerCase()
                    : "docx";
                return ResponseEntity.ok((Object) Map.of(
                    "id", dt.getId(),
                    "nombre", dt.getNombreArchivo() != null ? dt.getNombreArchivo() : "documento",
                    "tipoDocumento", ext,
                    "esDocumentoOficina", false,
                    "urlPresignada", presignedUrl,
                    "esTramite", true
                ));
            })
            .orElse(ResponseEntity.notFound().build());
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
        String presignedUrl = s3Service.generarUrlPresignada(key, 1440);
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
        String docId = documentoTramiteRepo.findByUrlS3(url)
            .map(dt -> dt.getId())
            .orElse("tramite_" + System.currentTimeMillis());
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("scriptUrl", documentoService.getOnlyofficeScriptUrl());
        config.put("documentType", docType);
        config.put("document", Map.of(
            "fileType", ext,
            "key",      docId + "_" + System.currentTimeMillis(),
            "title",    nombreArchivo,
            "url",      presignedUrl
        ));
        config.put("editorConfig", Map.of(
            "mode", modo,
            "user", Map.of("id", userId, "name", userId),
            "lang", "es"
        ));
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
}
