package com.workflow.tramite.controller;

import com.workflow.tramite.dto.TramiteDetalladoResponse;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.service.TramiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/tramites", "/tramites"})
@RequiredArgsConstructor
public class TramiteController {
    private final TramiteService tramiteService;

    @PostMapping
    public ResponseEntity<?> iniciarTramite(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(value = "userId", required = false) String userIdAttr,
            @RequestAttribute(value = "X-Empresa-Id", required = false) String empresaIdAttr,
            @RequestHeader(value = "Usuario-Id", required = false) String usuarioIdHeader,
            @RequestHeader(value = "Empresa-Id", required = false) String empresaIdHeader) {
        try {
            String usuarioId = userIdAttr != null ? userIdAttr : usuarioIdHeader;
            String empresaId = empresaIdAttr != null ? empresaIdAttr : empresaIdHeader;
            Tramite tramite = tramiteService.iniciarTramite(body, usuarioId, empresaId);
            return ResponseEntity.ok(tramite);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<?> listarTramites(@PathVariable String empresaId) {
        List<TramiteDetalladoResponse> tramites = tramiteService.listarTramitesEmpresaEnriquecidos(empresaId);
        return ResponseEntity.ok(Map.of("data", tramites));
    }

    @GetMapping("/politica/{politicaId}")
    public ResponseEntity<?> listarPorPolitica(@PathVariable String politicaId) {
        List<Tramite> tramites = tramiteService.listarTramitesPolitica(politicaId);
        return ResponseEntity.ok(Map.of("data", tramites));
    }

    @GetMapping("/departamento/{departamentoId}")
    public ResponseEntity<?> listarPorDepartamento(@PathVariable String departamentoId) {
        List<Tramite> tramites = tramiteService.listarTramitesDepartamento(departamentoId);
        return ResponseEntity.ok(Map.of("data", tramites));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerTramite(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("data", tramiteService.obtenerTramiteConEjecuciones(id)));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarTramite(
            @PathVariable String id,
            @RequestAttribute(value = "rol", required = false) String rol) {
        try {
            if (!"ADMIN_GENERAL".equals(rol)) {
                return ResponseEntity.status(403).body(Map.of("message", "Solo ADMIN_GENERAL puede cancelar trámites"));
            }
            Tramite tramite = tramiteService.cancelarTramite(id);
            return ResponseEntity.ok(Map.of("message", "Trámite cancelado", "data", tramite));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/monitor/{politicaId}")
    public ResponseEntity<?> monitor(@PathVariable String politicaId) {
        return ResponseEntity.ok(Map.of("data", tramiteService.obtenerEstadoMonitor(politicaId)));
    }
}
