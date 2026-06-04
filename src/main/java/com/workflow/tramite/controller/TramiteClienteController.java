package com.workflow.tramite.controller;

import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.dto.TramiteResumenClienteResponse;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cliente")
@RequiredArgsConstructor
@Slf4j
public class TramiteClienteController {

    private final TramiteRepository tramiteRepo;
    private final UsuarioRepository usuarioRepo;
    private final PoliticaRepository politicaRepo;
    private final EjecucionNodoRepository ejecucionNodoRepo;
    private final NodoRepository nodoRepo;

    @GetMapping("/mis-tramites")
    public ResponseEntity<?> misTramites(
            @RequestAttribute(value = "userId", required = false) String userId) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }

        try {
            List<Tramite> tramites = tramiteRepo.findByClienteIdOrderByIniciadoEnDesc(userId);

            Usuario cliente = usuarioRepo.findById(userId).orElse(null);
            String nombreCliente = cliente != null
                    ? cliente.getNombre().split(" ")[0]
                    : "Cliente";

            List<TramiteResumenClienteResponse> respuesta = tramites.stream().map(t -> {
                Politica pol = t.getPoliticaId() != null
                        ? politicaRepo.findById(t.getPoliticaId()).orElse(null)
                        : null;

                EjecucionNodo nodoActual = ejecucionNodoRepo
                        .findFirstByTramiteIdAndEstadoOrderByCreadoEnDesc(t.getId(), "EN_PROCESO")
                        .orElse(null);

                Nodo nodo = nodoActual != null && nodoActual.getNodoId() != null
                        ? nodoRepo.findById(nodoActual.getNodoId()).orElse(null)
                        : null;

                Usuario funcionario = nodoActual != null && nodoActual.getFuncionarioId() != null
                        ? usuarioRepo.findById(nodoActual.getFuncionarioId()).orElse(null)
                        : null;

                LocalDateTime inicio = t.getIniciadoEn() != null
                        ? t.getIniciadoEn()
                        : LocalDateTime.now();
                long minutos = ChronoUnit.MINUTES.between(inicio, LocalDateTime.now());

                String polNombre = pol != null ? pol.getNombre() : "Tramite";
                String nombre = nombreCliente + "_" + polNombre;

                String estado = t.getEstadoGeneral() != null ? t.getEstadoGeneral() : "EN_PROCESO";

                return TramiteResumenClienteResponse.builder()
                        .id(t.getId())
                        .nombre(nombre)
                        .politicaNombre(pol != null ? pol.getNombre() : "")
                        .asignadoA(funcionario != null ? funcionario.getNombre() : "Sin asignar")
                        .estado(estado)
                        .prioridad(calcularPrioridad(minutos, estado))
                        .iniciadoEn(inicio)
                        .tiempoTranscurridoMinutos((int) minutos)
                        .nodoActualNombre(nodo != null ? nodo.getNombre() : "")
                        .build();
            }).toList();

            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            log.error("Error al obtener tramites del cliente {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    private String calcularPrioridad(long minutos, String estado) {
        if ("COMPLETADO".equals(estado) || "RECHAZADO".equals(estado)) return "COMPLETADO";
        if (minutos > 1440) return "ALTA";
        if (minutos > 480) return "MEDIA";
        return "BAJA";
    }
}
