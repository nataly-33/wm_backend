package com.workflow.documento.controller;

import com.workflow.documento.dto.DocumentoRequest;
import com.workflow.documento.dto.DocumentoResponse;
import com.workflow.documento.model.AuditoriaDocumento;
import com.workflow.documento.model.DocumentoTramite;
import com.workflow.documento.model.PermisosDocumento;
import com.workflow.documento.repository.DocumentoTramiteRepository;
import com.workflow.documento.service.DocumentoService;
import com.workflow.documento.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;
    private final DocumentoTramiteRepository documentoTramiteRepo;
    private final S3Service s3Service;

    // ── Documentos (colección principal) ────────────────────────────────────

    @PostMapping("/api/v1/documentos/upload")
    public ResponseEntity<DocumentoResponse> subir(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart("datos") DocumentoRequest request,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        String userId = user.getUsername();
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

    @PostMapping("/api/v1/documentos/{id}/version")
    public ResponseEntity<DocumentoResponse> nuevaVersion(
            @PathVariable String id,
            @RequestPart("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        String userId = user.getUsername();
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
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(documentoService.cambiarPermisos(id, permisos, user.getUsername()));
    }

    @GetMapping("/api/v1/documentos/{id}/onlyoffice-config")
    public ResponseEntity<Map<String, Object>> onlyOfficeConfig(
            @PathVariable String id,
            @RequestParam(defaultValue = "view") String modo,
            @AuthenticationPrincipal UserDetails user) {
        String userId = user.getUsername();
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
}
