package com.workflow.ejecucion.controller;

import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.service.EjecucionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ejecuciones")
@RequiredArgsConstructor
public class EjecucionController {
    private final EjecucionService ejecucionService;

    @GetMapping("/departamento/{departamentoId}")
    public ResponseEntity<?> listarPorDepartamento(@PathVariable String departamentoId) {
        List<EjecucionNodo> ejecuciones = ejecucionService.listarPorDepartamento(departamentoId);
        return ResponseEntity.ok(Map.of("data", ejecuciones));
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
}
