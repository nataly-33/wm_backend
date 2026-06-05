package com.workflow.ejecucion.controller;

import com.workflow.ejecucion.dto.CampoConValor;
import com.workflow.ejecucion.dto.EjecucionDetalladaResponse;
import com.workflow.ejecucion.dto.FormularioRellenadoResponse;
import com.workflow.ejecucion.dto.VistaFuncionarioResponse;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.service.EjecucionService;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.model.Formulario.CampoFormulario;
import com.workflow.formulario.model.LlenadoPor;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.tramite.service.MotorWorkflowService;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/ejecuciones", "/ejecuciones"})
@RequiredArgsConstructor
public class EjecucionController {
    private final EjecucionService ejecucionService;
    private final MotorWorkflowService motorWorkflowService;
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final FormularioRepository formularioRepository;
    private final UsuarioRepository usuarioRepository;
    private final TramiteRepository tramiteRepository;

    @GetMapping("/departamento/{departamentoId}")
    public ResponseEntity<?> listarPorDepartamento(@PathVariable String departamentoId) {
        List<EjecucionNodo> ejecuciones = ejecucionService.listarPorDepartamento(departamentoId);
        return ResponseEntity.ok(Map.of("data", ejecuciones));
    }

    @GetMapping("/funcionario/{usuarioId}")
    public ResponseEntity<?> listarPorFuncionario(@PathVariable String usuarioId) {
        List<EjecucionDetalladaResponse> ejecuciones = ejecucionService.listarPorFuncionarioDetallado(usuarioId);
        return ResponseEntity.ok(Map.of("data", ejecuciones));
    }

    @GetMapping("/funcionario/{usuarioId}/historial")
    public ResponseEntity<?> listarHistorialPorFuncionario(@PathVariable String usuarioId) {
        List<EjecucionDetalladaResponse> ejecuciones = ejecucionService.listarHistorialPorFuncionarioDetallado(usuarioId);
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

    /**
     * Historial de formularios rellenados en un tramite.
     * Accesible para ADMIN_GENERAL y FUNCIONARIO.
     */
    @GetMapping("/tramite/{tramiteId}/historial-formularios")
    public ResponseEntity<?> historialFormularios(@PathVariable String tramiteId) {
        try {
            List<FormularioRellenadoResponse> historial = ejecucionService.obtenerHistorialFormularios(tramiteId);
            return ResponseEntity.ok(Map.of("data", historial));
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

    /** El funcionario revisa y aprueba/rechaza el nodo. */
    @PutMapping("/{id}/funcionario-completar")
    public ResponseEntity<?> funcionarioCompletar(
            @PathVariable String id,
            @RequestBody Map<String, Object> respuestas,
            @AuthenticationPrincipal UserDetails user) {
        try {
            // Resolver el ID MongoDB del usuario a partir de su email (username del JWT)
            String funcionarioId = null;
            if (user != null) {
                funcionarioId = usuarioRepository.findByEmailAndActivoTrue(user.getUsername())
                        .map(u -> u.getId())
                        .orElse(user.getUsername()); // fallback al email si no se encuentra
            }
            motorWorkflowService.funcionarioCompletadoNodo(id, respuestas, funcionarioId);
            return ResponseEntity.ok(Map.of("mensaje", "Nodo completado por funcionario"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }

    /** Vista del funcionario: campos del cliente (con valores) + campos que él debe rellenar. */
    @GetMapping("/{id}/vista-funcionario")
    public ResponseEntity<?> vistaFuncionario(@PathVariable String id) {
        try {
            EjecucionNodo ejec = ejecucionNodoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Ejecución no encontrada"));

            Formulario form = formularioRepository.findByNodoId(ejec.getNodoId())
                    .orElseThrow(() -> new RuntimeException("Formulario no encontrado para el nodo"));

            Map<String, Object> respuestasCliente = ejec.getRespuestasCliente() != null
                    ? ejec.getRespuestasCliente()
                    : Map.of();

            List<CampoConValor> camposCliente = form.getCampos() == null ? List.of() :
                    form.getCampos().stream()
                            .filter(c -> c.getLlenadoPor() == LlenadoPor.CLIENTE)
                            .map(c -> CampoConValor.builder()
                                    .campo(c)
                                    .valor(respuestasCliente.get(c.getNombre()))
                                    .build())
                            .toList();

            List<CampoFormulario> camposFuncionario = form.getCampos() == null ? List.of() :
                    form.getCampos().stream()
                            .filter(c -> c.getLlenadoPor() == null || c.getLlenadoPor() == LlenadoPor.FUNCIONARIO)
                            .toList();

            Tramite tramite = ejec.getTramiteId() != null 
                    ? tramiteRepository.findById(ejec.getTramiteId()).orElse(null) 
                    : null;

            VistaFuncionarioResponse vista = VistaFuncionarioResponse.builder()
                    .ejecucionId(id)
                    .tramiteId(ejec.getTramiteId())
                    .departamentoId(ejec.getDepartamentoId())
                    .clienteId(tramite != null ? tramite.getClienteId() : null)
                    .empresaId(tramite != null ? tramite.getEmpresaId() : null)
                    .fase(ejec.getFase() != null ? ejec.getFase().toString() : null)
                    .camposCliente(camposCliente)
                    .camposFuncionario(camposFuncionario)
                    .build();

            return ResponseEntity.ok(vista);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }
}
