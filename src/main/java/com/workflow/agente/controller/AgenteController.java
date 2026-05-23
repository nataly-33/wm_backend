package com.workflow.agente.controller;

import com.workflow.agente.dto.EstadoTramiteClienteResponse;
import com.workflow.agente.dto.MensajeChatRequest;
import com.workflow.agente.dto.SubirDocumentoAgenteRequest;
import com.workflow.agente.model.ConversacionAgente;
import com.workflow.agente.service.AgenteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cliente/agente")
@RequiredArgsConstructor
@Slf4j
public class AgenteController {

    private final AgenteService agenteService;

    /**
     * Enviar mensaje al agente conversacional.
     * El clienteId se extrae del token JWT via atributo de request.
     */
    @PostMapping("/mensaje")
    public ResponseEntity<Map<String, Object>> enviarMensaje(
            @RequestBody MensajeChatRequest request,
            @RequestAttribute(value = "userId", required = false) String userId) {
        try {
            String clienteId = request.getClienteId() != null ? request.getClienteId() : userId;
            Map<String, Object> respuesta = agenteService.procesarMensaje(
                    request.getConversacionId(),
                    clienteId,
                    request.getMensaje(),
                    request.getTipo()
            );
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            log.error("Error procesando mensaje del agente: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "mensajeAgente", "Lo siento, ocurrio un error. Por favor intenta de nuevo.",
                    "estado", "ERROR"
            ));
        }
    }

    /**
     * Notificar al agente que se subio un archivo.
     */
    @PostMapping("/subir-archivo")
    public ResponseEntity<Map<String, Object>> subirArchivo(
            @RequestBody SubirDocumentoAgenteRequest request,
            @RequestAttribute(value = "userId", required = false) String userId) {
        try {
            String clienteId = request.getClienteId() != null ? request.getClienteId() : userId;
            Map<String, Object> respuesta = agenteService.procesarArchivoSubido(
                    request.getConversacionId(),
                    clienteId,
                    request.getArchivoUrl(),
                    request.getNombreArchivo()
            );
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            log.error("Error procesando archivo del agente: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Historial de conversaciones del cliente.
     */
    @GetMapping("/historial")
    public ResponseEntity<?> obtenerHistorial(
            @RequestParam(required = false) String clienteId,
            @RequestAttribute(value = "userId", required = false) String userId) {
        String id = clienteId != null ? clienteId : userId;
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Se requiere clienteId"));
        }
        List<ConversacionAgente> historial = agenteService.obtenerHistorialCliente(id);
        return ResponseEntity.ok(Map.of("data", historial));
    }

    /**
     * Estado actual del tramite del cliente.
     */
    @GetMapping("/estado-tramite/{tramiteId}")
    public ResponseEntity<?> obtenerEstadoTramite(@PathVariable String tramiteId) {
        try {
            EstadoTramiteClienteResponse estado = agenteService.obtenerEstadoTramite(tramiteId);
            return ResponseEntity.ok(Map.of("data", estado));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
