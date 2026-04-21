package com.workflow.ejecucion.controller;

import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.service.EjecucionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/ejecuciones", "/ejecuciones"})
@RequiredArgsConstructor
public class EjecucionController {
    private final EjecucionService ejecucionService;

    @GetMapping("/departamento/{departamentoId}")
    public ResponseEntity<?> listarPorDepartamento(@PathVariable String departamentoId) {
        List<EjecucionNodo> ejecuciones = ejecucionService.listarPorDepartamento(departamentoId);
        return ResponseEntity.ok(Map.of("data", ejecuciones));
    }

    @GetMapping("/funcionario/{usuarioId}")
    public ResponseEntity<?> listarPorFuncionario(@PathVariable String usuarioId) {
        List<EjecucionNodo> ejecuciones = ejecucionService.listarPorFuncionario(usuarioId);
        return ResponseEntity.ok(Map.of("data", ejecuciones));
    }

    @GetMapping("/tramite/{tramiteId}")
    public ResponseEntity<?> listarPorTramite(@PathVariable String tramiteId) {
        List<EjecucionNodo> ejecuciones = ejecucionService.listarPorTramite(tramiteId);
        return ResponseEntity.ok(Map.of("data", ejecuciones));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("data", ejecucionService.obtener(id)));
    }

    @PutMapping("/{id}/iniciar")
    public ResponseEntity<?> iniciarEjecucion(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId) {
        try {
            EjecucionNodo ejecucion = ejecucionService.iniciar(id, userId);
            return ResponseEntity.ok(Map.of("message", "Ejecución iniciada", "data", ejecucion));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/completar")
    public ResponseEntity<?> completarEjecucion(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = (Map<String, Object>) body.get("respuesta_formulario");
            ejecucionService.completar(id, respuesta);
            return ResponseEntity.ok(Map.of("message", "Ejecución completada exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<?> rechazarEjecucion(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String observaciones = (String) body.get("observaciones");
            ejecucionService.rechazar(id, observaciones);
            return ResponseEntity.ok(Map.of("message", "Ejecución rechazada"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reasignar")
    public ResponseEntity<?> reasignar(
            @PathVariable String id,
            @RequestAttribute(value = "rol", required = false) String rol,
            @RequestBody Map<String, Object> body) {
        try {
            if (!"ADMIN_GENERAL".equals(rol)) {
                return ResponseEntity.status(403).body(Map.of("message", "Solo ADMIN_GENERAL puede reasignar"));
            }

            String funcionarioId = (String) body.get("funcionarioId");
            if (funcionarioId == null || funcionarioId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "funcionarioId es obligatorio"));
            }

            EjecucionNodo actualizada = ejecucionService.reasignar(id, funcionarioId);
            return ResponseEntity.ok(Map.of("message", "Ejecución reasignada", "data", actualizada));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
