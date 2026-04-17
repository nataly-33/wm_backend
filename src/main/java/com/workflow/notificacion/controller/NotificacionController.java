package com.workflow.notificacion.controller;

import com.workflow.notificacion.model.Notificacion;
import com.workflow.notificacion.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {
    private final NotificacionService notificacionService;

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obtenerNoLeidas(@PathVariable String usuarioId) {
        List<Notificacion> notificaciones = notificacionService.obtenerNoLeidas(usuarioId);
        return ResponseEntity.ok(Map.of("data", notificaciones));
    }

    @PutMapping("/{id}/leer")
    public ResponseEntity<?> marcarLeida(@PathVariable String id) {
        notificacionService.marcarComoLeida(id);
        return ResponseEntity.ok(Map.of("message", "Notificación marcada como leída"));
    }
}
