package com.workflow.documento.controller;

import com.workflow.documento.dto.DocumentoRequest;
import com.workflow.documento.dto.DocumentoResponse;
import com.workflow.documento.model.AuditoriaDocumento;
import com.workflow.documento.model.PermisosDocumento;
import com.workflow.documento.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentoResponse> subir(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestPart("datos") DocumentoRequest request,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        String userId = user.getUsername();
        return ResponseEntity.ok(documentoService.subirDocumento(archivo, request, userId, userId));
    }

    @GetMapping
    public ResponseEntity<List<DocumentoResponse>> listar(
            @RequestParam String empresaId,
            @RequestParam(required = false) String carpetaId,
            @RequestParam(required = false) String politicaId,
            @RequestParam(required = false) String tramiteId) {
        return ResponseEntity.ok(documentoService.listar(empresaId, carpetaId, politicaId, tramiteId));
    }

    @PostMapping("/{id}/version")
    public ResponseEntity<DocumentoResponse> nuevaVersion(
            @PathVariable String id,
            @RequestPart("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UserDetails user) throws IOException {
        String userId = user.getUsername();
        return ResponseEntity.ok(documentoService.subirNuevaVersion(id, archivo, userId, userId));
    }

    @GetMapping("/{id}/auditoria")
    public ResponseEntity<List<AuditoriaDocumento>> auditoria(@PathVariable String id) {
        return ResponseEntity.ok(documentoService.obtenerAuditoria(id));
    }

    @PutMapping("/{id}/permisos")
    public ResponseEntity<DocumentoResponse> cambiarPermisos(
            @PathVariable String id,
            @RequestBody PermisosDocumento permisos,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(documentoService.cambiarPermisos(id, permisos, user.getUsername()));
    }

    @GetMapping("/{id}/onlyoffice-config")
    public ResponseEntity<Map<String, Object>> onlyOfficeConfig(
            @PathVariable String id,
            @RequestParam(defaultValue = "view") String modo,
            @AuthenticationPrincipal UserDetails user) {
        String userId = user.getUsername();
        return ResponseEntity.ok(documentoService.generarConfigOnlyOffice(id, userId, userId, modo));
    }
}
