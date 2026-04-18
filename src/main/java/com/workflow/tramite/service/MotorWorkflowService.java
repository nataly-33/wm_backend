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
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.model.Formulario.CampoFormulario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final FormularioRepository formularioRepository;

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
                // Caso 3: Buscamos el formulario del nodo anterior y vemos iterando qué campo tiene esCampoPrioridad = true
                String valorDecision = "";
                Map<String, Object> resp = ejecucionAnterior.getRespuestaFormulario();
                Formulario f = formularioRepository.findByNodoIdAndActivoTrue(ejecucionAnterior.getNodoId()).orElse(null);
                
                if (f != null && resp != null) {
                    for (CampoFormulario campo : f.getCampos()) {
                        if (Boolean.TRUE.equals(campo.getEsCampoPrioridad())) {
                            valorDecision = String.valueOf(resp.get(campo.getNombre()));
                            break;
                        }
                    }
                }

                for (Transicion t : salientes) {
                    if (t.getEtiqueta() != null && t.getEtiqueta().equalsIgnoreCase(valorDecision)) {
                        transicionSeguida = t;
                        break;
                    }
                }
                if (transicionSeguida == null) {
                    tramite.setEstadoGeneral("BLOQUEADO");
                    tramiteRepository.save(tramite);
                    throw new RuntimeException("El tramite ha sido BLOQUEADO. No hubo transicion Alternativa valida para: " + valorDecision);
                }
            } else {
                transicionSeguida = salientes.get(0); // LINEAL pero mas de 1 (error de diseño del usuario)
            }
        }

        if (transicionesParalelas != null) {
            // FORK PARALELO (Caso 2)
            List<String> nodosDestino = new ArrayList<>();
            for (Transicion t : transicionesParalelas) {
                moverANodo(tramite, t.getNodoDestinoId());
                nodosDestino.add(t.getNodoDestinoId());
            }
            tramite.setNodoActualId("NODO_PARALELO"); 
            tramite.setNodosParalelosPendientes(nodosDestino);
        } else if (transicionSeguida != null) {
            moverANodo(tramite, transicionSeguida.getNodoDestinoId());
            if (tramite.getNodosParalelosPendientes() != null) {
                tramite.getNodosParalelosPendientes().remove(ejecucionAnterior.getNodoId());
                // Si aún quedan nodos paralelos por terminar, el tramite sigue esperando el JOIN
                if (!tramite.getNodosParalelosPendientes().isEmpty()) {
                    // Update the string array and don't overwrite current primary node tracking completely until Join is done
                    tramiteRepository.save(tramite);
                    return;
                }
            }
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

        // Caso 4: Ciclos. Contabilizamos las iteraciones por nodo.
        Map<String, Integer> iteraciones = tramite.getIteracionesPorNodo();
        if (iteraciones == null) iteraciones = new HashMap<>();
        iteraciones.put(nodoId, iteraciones.getOrDefault(nodoId, 0) + 1);
        tramite.setIteracionesPorNodo(iteraciones);

        // Caso 1: Validacion funcionario/admin departamento
        String funcionarioAsignadoId = null;
        String estadoAsignacion = "PENDIENTE";

        List<Usuario> funcionarios = usuarioRepository.findByDepartamentoIdAndActivoTrue(nodoDestino.getDepartamentoId());
        if (funcionarios == null || funcionarios.isEmpty()) {
            Departamento depto = departamentoRepository.findById(nodoDestino.getDepartamentoId()).orElse(null);
            if (depto != null && depto.getAdminDepartamentoId() != null) {
                funcionarioAsignadoId = depto.getAdminDepartamentoId();
            } else {
                estadoAsignacion = "PENDIENTE_SIN_ASIGNAR";
                // TODO Notificar Admin General que no hay a quien asignarle el trámite
            }
        }

        EjecucionNodo nuevaEjecucion = EjecucionNodo.builder()
            .tramiteId(tramite.getId())
            .nodoId(nodoId)
            .departamentoId(nodoDestino.getDepartamentoId())
            .funcionarioId(funcionarioAsignadoId)
            .estado(estadoAsignacion)
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
