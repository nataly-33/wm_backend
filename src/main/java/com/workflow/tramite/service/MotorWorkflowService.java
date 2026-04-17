package com.workflow.tramite.service;

import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.notificacion.service.NotificacionService;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotorWorkflowService {
    private final TramiteRepository tramiteRepository;
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final NodoRepository nodoRepository;
    private final TransicionRepository transicionRepository;
    private final NotificacionService notificacionService;

    public Tramite iniciarTramite(Tramite tramite, String nodoInicioId) {
        tramite.setEstadoGeneral("EN_PROCESO");
        tramite.setNodoActualId(nodoInicioId);
        tramite.setIniciadoEn(LocalDateTime.now());
        Tramite savedTramite = tramiteRepository.save(tramite);

        // Emitir al monitor
        notificarMonitor(savedTramite);

        // Crear la primera ejecucion para el nodo inicio
        moverANodo(savedTramite, nodoInicioId);

        return savedTramite;
    }

    public void completarEjecucion(String ejecucionId, Map<String, Object> respuestaFormulario) {
        EjecucionNodo ejecucion = ejecucionNodoRepository.findById(ejecucionId)
            .orElseThrow(() -> new RuntimeException("Ejecución no encontrada"));

        if (!"PENDIENTE".equals(ejecucion.getEstado()) && !"EN_PROCESO".equals(ejecucion.getEstado())) {
            throw new RuntimeException("Solo se pueden completar ejecuciones PENDIENTES o EN_PROCESO");
        }

        ejecucion.setEstado("COMPLETADO");
        ejecucion.setRespuestaFormulario(respuestaFormulario);
        ejecucion.setCompletadoEn(LocalDateTime.now());
        ejecucionNodoRepository.save(ejecucion);

        Tramite tramite = tramiteRepository.findById(ejecucion.getTramiteId())
            .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

        avanzarTramite(tramite, ejecucion);
    }

    public void rechazarEjecucion(String ejecucionId, String observaciones) {
        EjecucionNodo ejecucion = ejecucionNodoRepository.findById(ejecucionId)
            .orElseThrow(() -> new RuntimeException("Ejecución no encontrada"));

        ejecucion.setEstado("RECHAZADO");
        ejecucion.setObservaciones(observaciones);
        ejecucion.setCompletadoEn(LocalDateTime.now());
        ejecucionNodoRepository.save(ejecucion);

        Tramite tramite = tramiteRepository.findById(ejecucion.getTramiteId())
            .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

        tramite.setEstadoGeneral("RECHAZADO");
        tramite.setFinalizadoEn(LocalDateTime.now());
        tramiteRepository.save(tramite);

        // Notificar iniciador
        notificacionService.crearNotificacion(
            tramite.getIniciadoPor(), tramite.getId(), ejecucion.getNodoId(),
            "RECHAZADO", "Tu trámite '" + tramite.getTitulo() + "' ha sido rechazado."
        );

        notificarMonitor(tramite);
    }

    private void avanzarTramite(Tramite tramite, EjecucionNodo ejecucionAnterior) {
        // Encontrar transiciones salientes del nodo recien completado
        List<Transicion> salientes = transicionRepository.findByNodoOrigenId(ejecucionAnterior.getNodoId());

        if (salientes.isEmpty()) {
            marcarTramiteComoCompletado(tramite);
            return;
        }

        Transicion transicionSeguida = null;
        List<Transicion> transicionesParalelas = null;

        // Si solo hay una linea, la seguimos
        if (salientes.size() == 1) {
            transicionSeguida = salientes.get(0);
        } else {
            // Logica de ALTERNATIVA o PARALELA
            Transicion posibleAlternativa = salientes.get(0);
            if ("PARALELA".equals(posibleAlternativa.getTipo())) {
                transicionesParalelas = salientes;
            } else if ("ALTERNATIVA".equals(posibleAlternativa.getTipo())) {
                // Sacar valor de es_campo_prioridad y comparar con "etiqueta"
                // Para simplificar, buscamos en el map la condicion (ej label "Aprobado")
                Map<String, Object> resp = ejecucionAnterior.getRespuestaFormulario();
                String valorDecision = resp != null ? String.valueOf(resp.values().stream().findFirst().orElse("")) : "";
                
                for (Transicion t : salientes) {
                    if (t.getEtiqueta() != null && t.getEtiqueta().equalsIgnoreCase(valorDecision)) {
                        transicionSeguida = t;
                        break;
                    }
                }
                if (transicionSeguida == null) {
                    // Fallback a defecto
                    transicionSeguida = salientes.get(0);
                }
            } else {
                transicionSeguida = salientes.get(0); // LINEAL pero mas de 1 (error de diseño del usuario)
            }
        }

        if (transicionesParalelas != null) {
            for (Transicion t : transicionesParalelas) {
                moverANodo(tramite, t.getNodoDestinoId());
            }
            tramite.setNodoActualId(transicionesParalelas.get(0).getNodoDestinoId()); // Aproximacion a paralelo
        } else if (transicionSeguida != null) {
            moverANodo(tramite, transicionSeguida.getNodoDestinoId());
            tramite.setNodoActualId(transicionSeguida.getNodoDestinoId());
        }

        tramiteRepository.save(tramite);
        notificarMonitor(tramite);
    }

    private void moverANodo(Tramite tramite, String nodoId) {
        Nodo nodoDestino = nodoRepository.findById(nodoId)
            .orElseThrow(() -> new RuntimeException("Nodo destino no encontrado"));

        if ("FIN".equals(nodoDestino.getTipo())) {
            marcarTramiteComoCompletado(tramite);
            return;
        }

        EjecucionNodo nuevaEjecucion = EjecucionNodo.builder()
            .tramiteId(tramite.getId())
            .nodoId(nodoId)
            .departamentoId(nodoDestino.getDepartamentoId())
            .estado("PENDIENTE")
            .iniciadoEn(LocalDateTime.now())
            .build();
        
        ejecucionNodoRepository.save(nuevaEjecucion);

        // TODO: Push flutter
        // Para este plan, ya se maneja notificacion a administradores o asignados localmente
    }

    private void marcarTramiteComoCompletado(Tramite tramite) {
        tramite.setEstadoGeneral("COMPLETADO");
        tramite.setFinalizadoEn(LocalDateTime.now());
        tramiteRepository.save(tramite);

        // Notificar iniciador
        notificacionService.crearNotificacion(
            tramite.getIniciadoPor(), tramite.getId(), tramite.getNodoActualId(),
            "COMPLETADO", "Tu trámite '" + tramite.getTitulo() + "' ha sido completado."
        );
        notificarMonitor(tramite);
    }

    private void notificarMonitor(Tramite tramite) {
        notificacionService.notificarCambioMonitor(tramite.getPoliticaId(), Map.of(
            "evento", "CAMBIO_ESTADO",
            "tramiteId", tramite.getId(),
            "estado", tramite.getEstadoGeneral()
        ));
    }
}
