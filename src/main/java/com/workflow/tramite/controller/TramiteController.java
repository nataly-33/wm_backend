package com.workflow.tramite.controller;

import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.service.TramiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tramites")
@RequiredArgsConstructor
public class TramiteController {
    private final TramiteService tramiteService;

    @PostMapping
    public ResponseEntity<?> iniciarTramite(@RequestBody Map<String, Object> body, @RequestHeader(value = "Usuario-Id", required = false) String usuarioId, @RequestHeader(value = "Empresa-Id", required = false) String empresaId) {
        // En un entorno real, extraer usuarioId y empresaId del token SecurityContext.
        // Aquí usaremos unos provistos por simplicidad o mocks si faltan.
        try {
            Tramite tramite = tramiteService.iniciarTramite(body, usuarioId, empresaId);
            return ResponseEntity.ok(tramite);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<?> listarTramites(@PathVariable String empresaId) {
        List<Tramite> tramites = tramiteService.listarTramitesEmpresa(empresaId);
        return ResponseEntity.ok(Map.of("data", tramites));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerTramite(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("data", tramiteService.obtenerTramite(id)));
    }
}
