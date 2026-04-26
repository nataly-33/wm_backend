package com.workflow.tramite.service;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.model.Formulario.CampoFormulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.notificacion.service.NotificacionService;
import com.workflow.notificacion.service.PushNotificacionService;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotorWorkflowService {
    private static final List<String> ESTADOS_ACTIVOS_FUNCIONARIO = List.of("PENDIENTE", "EN_PROCESO");

    private final TramiteRepository tramiteRepository;
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final NodoRepository nodoRepository;
    private final TransicionRepository transicionRepository;
    private final NotificacionService notificacionService;
    private final PushNotificacionService pushNotificacionService;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final FormularioRepository formularioRepository;
    private final PoliticaRepository politicaRepository;

    public Tramite iniciarTramite(Tramite tramite, String nodoInicioId) {
        tramite.setEstadoGeneral("PENDIENTE");
        tramite.setNodoActualId(null);
        tramite.setIniciadoEn(LocalDateTime.now());
        if (tramite.getIteracionesPorNodo() == null) {
            tramite.setIteracionesPorNodo(new HashMap<>());
        }
        if (tramite.getNodosParalelosPendientes() == null) {
            tramite.setNodosParalelosPendientes(new ArrayList<>());
        }

        Tramite savedTramite = tramiteRepository.save(tramite);

        List<Transicion> salientesInicio = salientesActivas(nodoInicioId);
        if (salientesInicio.isEmpty()) {
            marcarTramiteComoCompletado(savedTramite);
            return savedTramite;
        }

        if (esForkParalelo(salientesInicio)) {
            List<String> nodosDestino = new ArrayList<>();
            for (Transicion t : salientesInicio) {
                moverANodo(savedTramite, t.getNodoDestinoId());
                nodosDestino.add(t.getNodoDestinoId());
            }
            savedTramite.setNodoActualId(null);
            savedTramite.setNodosParalelosPendientes(nodosDestino);
        } else {
            Transicion transicion = seleccionarTransicionLineal(salientesInicio);
            moverANodo(savedTramite, transicion.getNodoDestinoId());
        }

        tramiteRepository.save(savedTramite);

        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", "TRAMITE_INICIADO");
        evento.put("tramiteId", savedTramite.getId());
        evento.put("titulo", savedTramite.getTitulo());
        evento.put("nodoActualId", savedTramite.getNodoActualId());
        evento.put("prioridad", savedTramite.getPrioridad());
        notificacionService.notificarCambioMonitor(savedTramite.getPoliticaId(), evento);

        return savedTramite;
    }

    public void completarEjecucion(String ejecucionId, Map<String, Object> respuestaFormulario) {
        EjecucionNodo ejecucion = ejecucionNodoRepository.findById(ejecucionId)
                .orElseThrow(() -> new RuntimeException("Ejecución no encontrada"));

        if (!"PENDIENTE".equals(ejecucion.getEstado()) && !"EN_PROCESO".equals(ejecucion.getEstado())) {
            throw new RuntimeException("Solo se pueden completar ejecuciones PENDIENTES o EN_PROCESO");
        }

        if (ejecucion.getIniciadoEn() == null) {
            ejecucion.setIniciadoEn(LocalDateTime.now());
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

        if (!"PENDIENTE".equals(ejecucion.getEstado()) && !"EN_PROCESO".equals(ejecucion.getEstado())) {
            throw new RuntimeException("Solo se pueden rechazar ejecuciones PENDIENTES o EN_PROCESO");
        }

        if (ejecucion.getIniciadoEn() == null) {
            ejecucion.setIniciadoEn(LocalDateTime.now());
        }

        ejecucion.setEstado("RECHAZADO");
        ejecucion.setObservaciones(observaciones);
        ejecucion.setCompletadoEn(LocalDateTime.now());
        ejecucionNodoRepository.save(ejecucion);

        Tramite tramite = tramiteRepository.findById(ejecucion.getTramiteId())
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));

        tramite.setEstadoGeneral("RECHAZADO");
        tramite.setFinalizadoEn(LocalDateTime.now());
        tramiteRepository.save(tramite);

        notificarAdminsGenerales(
                tramite.getEmpresaId(),
                tramite.getId(),
                ejecucion.getNodoId(),
                "RECHAZADO",
                "El trámite '" + tramite.getTitulo() + "' fue rechazado. Observaciones: "
                        + (observaciones == null ? "(sin observaciones)" : observaciones)
        );

        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", "TRAMITE_RECHAZADO");
        evento.put("tramiteId", tramite.getId());
        evento.put("observaciones", observaciones);
        notificacionService.notificarCambioMonitor(tramite.getPoliticaId(), evento);

        // Push al Admin General
        List<Usuario> adminsRechazo = usuarioRepository.findByEmpresaIdAndRolAndActivoTrue(tramite.getEmpresaId(), "ADMIN_GENERAL");
        adminsRechazo.stream().filter(a -> a.getFcmToken() != null && !a.getFcmToken().isBlank()).forEach(a ->
            pushNotificacionService.enviarPush(
                a.getFcmToken(),
                "Trámite rechazado: " + tramite.getTitulo(),
                observaciones != null ? observaciones : "El trámite fue rechazado.",
                Map.of("tipo", "RECHAZADO", "tramiteId", tramite.getId())
            )
        );
    }

    private void avanzarTramite(Tramite tramite, EjecucionNodo ejecucionAnterior) {
        String nodoCompletado = ejecucionAnterior.getNodoId();
        String siguienteNodoParaEvento = null;

        if (tramite.getNodosParalelosPendientes() != null && !tramite.getNodosParalelosPendientes().isEmpty()) {
            boolean eraRamaParalela = tramite.getNodosParalelosPendientes().stream()
                    .anyMatch(id -> id.equals(nodoCompletado));
            if (eraRamaParalela) {
                tramite.getNodosParalelosPendientes().removeIf(id -> id.equals(nodoCompletado));
                if (!tramite.getNodosParalelosPendientes().isEmpty()) {
                    tramite.setEstadoGeneral("EN_PROCESO");
                    tramiteRepository.save(tramite);
                    emitirEventoNodoCompletado(tramite, nodoCompletado, null);
                    return;
                }

                tramite.setNodosParalelosPendientes(new ArrayList<>());
                List<Transicion> salientesJoin = salientesActivas(nodoCompletado);
                if (salientesJoin.isEmpty()) {
                    marcarTramiteComoCompletado(tramite);
                    return;
                }

                Transicion transicion = seleccionarTransicionLineal(salientesJoin);
                moverANodo(tramite, transicion.getNodoDestinoId());
                if ("COMPLETADO".equals(tramite.getEstadoGeneral())) {
                    return; // Nodo FIN alcanzado desde join paralelo
                }
                siguienteNodoParaEvento = tramite.getNodoActualId();
                tramite.setEstadoGeneral("EN_PROCESO");
                tramiteRepository.save(tramite);
                emitirEventoNodoCompletado(tramite, nodoCompletado, siguienteNodoParaEvento);
                return;
            }
        }

        List<Transicion> salientes = salientesActivas(nodoCompletado);
        if (salientes.isEmpty()) {
            marcarTramiteComoCompletado(tramite);
            return;
        }

        Transicion transicionSeguida = null;
        List<Transicion> transicionesParalelas = null;

        if (salientes.size() == 1) {
            transicionSeguida = salientes.get(0);
        } else {
            Transicion primera = salientes.get(0);
            if ("PARALELA".equals(primera.getTipo())) {
                boolean todasParalelas = salientes.stream().allMatch(t -> "PARALELA".equals(t.getTipo()));
                transicionesParalelas = todasParalelas ? salientes : null;
            }
            if (transicionesParalelas == null && "ALTERNATIVA".equals(primera.getTipo())) {
                String valorDecision = extraerValorDecision(ejecucionAnterior);

                String vd = valorDecision.trim();
                for (Transicion t : salientes) {
                    if ("ALTERNATIVA".equals(t.getTipo()) && t.getEtiqueta() != null
                            && t.getEtiqueta().trim().equalsIgnoreCase(vd)) {
                        transicionSeguida = t;
                        break;
                    }
                }
                if (transicionSeguida == null) {
                    tramite.setEstadoGeneral("BLOQUEADO");
                    tramiteRepository.save(tramite);
                    notificarAdminsGenerales(
                            tramite.getEmpresaId(),
                            tramite.getId(),
                            ejecucionAnterior.getNodoId(),
                            "BLOQUEADO",
                            "El trámite '" + tramite.getTitulo()
                                    + "' quedó bloqueado. No existe transición ALTERNATIVA para: " + valorDecision
                    );
                    throw new RuntimeException(
                            "El tramite ha sido BLOQUEADO. No hubo transicion Alternativa valida para: " + valorDecision
                    );
                }
            } else if (transicionesParalelas == null) {
                transicionSeguida = seleccionarTransicionLineal(salientes);
            }
        }

        if (transicionesParalelas != null) {
            List<String> nodosDestino = new ArrayList<>();
            for (Transicion t : transicionesParalelas) {
                moverANodo(tramite, t.getNodoDestinoId());
                nodosDestino.add(t.getNodoDestinoId());
            }
            tramite.setNodoActualId(null);
            tramite.setNodosParalelosPendientes(nodosDestino);
            tramite.setEstadoGeneral("EN_PROCESO");
        } else if (transicionSeguida != null) {
            moverANodo(tramite, transicionSeguida.getNodoDestinoId());
            if ("COMPLETADO".equals(tramite.getEstadoGeneral())) {
                return; // Nodo FIN alcanzado: marcarTramiteComoCompletado ya guardó y notificó
            }
            siguienteNodoParaEvento = tramite.getNodoActualId();
            tramite.setEstadoGeneral("EN_PROCESO");
        }

        tramiteRepository.save(tramite);
        emitirEventoNodoCompletado(tramite, nodoCompletado, siguienteNodoParaEvento);
    }

    private void moverANodo(Tramite tramite, String nodoId) {
        Nodo nodoDestino = nodoRepository.findById(nodoId)
                .orElseThrow(() -> new RuntimeException("Nodo destino no encontrado"));

        Map<String, Integer> iteraciones = tramite.getIteracionesPorNodo();
        if (iteraciones == null) {
            iteraciones = new HashMap<>();
        }
        iteraciones.put(nodoId, iteraciones.getOrDefault(nodoId, 0) + 1);
        tramite.setIteracionesPorNodo(iteraciones);

        if ("FIN".equals(nodoDestino.getTipo())) {
            marcarTramiteComoCompletado(tramite);
            return;
        }

        if ("PARALELO".equals(nodoDestino.getTipo())) {
            List<Transicion> salientes = salientesActivas(nodoDestino.getId());
            if (salientes.isEmpty()) {
                marcarTramiteComoCompletado(tramite);
                return;
            }

            if (esForkParalelo(salientes)) {
                List<String> nodosDestinoParalelos = new ArrayList<>();
                for (Transicion t : salientes) {
                    moverANodo(tramite, t.getNodoDestinoId());
                    nodosDestinoParalelos.add(t.getNodoDestinoId());
                }
                tramite.setNodoActualId(null);
                tramite.setNodosParalelosPendientes(nodosDestinoParalelos);
                return;
            }

            Transicion transicion = seleccionarTransicionLineal(salientes);
            moverANodo(tramite, transicion.getNodoDestinoId());
            return;
        }

        AsignacionUsuario asignacion = buscarFuncionarioAsignado(tramite.getEmpresaId(), nodoDestino.getDepartamentoId());

        EjecucionNodo nuevaEjecucion = EjecucionNodo.builder()
                .tramiteId(tramite.getId())
                .nodoId(nodoId)
                .departamentoId(nodoDestino.getDepartamentoId())
                .funcionarioId(asignacion.funcionarioId())
                .estado(asignacion.estado())
                .iniciadoEn(null)
                .respuestaFormulario(null)
                .archivosAdjuntos(new ArrayList<>())
                .build();

        nuevaEjecucion = ejecucionNodoRepository.save(nuevaEjecucion);

        tramite.setNodoActualId(nodoId);

        if ("PENDIENTE_SIN_ASIGNAR".equals(asignacion.estado())) {
            Departamento depto = departamentoRepository.findById(nodoDestino.getDepartamentoId()).orElse(null);
            String nombreDepto = depto != null ? depto.getNombre() : nodoDestino.getDepartamentoId();
            notificarAdminsGenerales(
                    tramite.getEmpresaId(),
                    tramite.getId(),
                    nodoDestino.getId(),
                    "URGENTE",
                    "El trámite '" + tramite.getTitulo() + "' está bloqueado: el departamento '"
                            + nombreDepto + "' no tiene personal asignado"
            );
        } else {
            notificarAsignacionFuncionario(tramite, nuevaEjecucion, nodoDestino);
        }
    }

    private void notificarAsignacionFuncionario(Tramite tramite, EjecucionNodo ejecucion, Nodo nodo) {
        if (ejecucion.getFuncionarioId() == null || ejecucion.getFuncionarioId().isBlank()) {
            return;
        }

        Politica politica = politicaRepository.findById(tramite.getPoliticaId()).orElse(null);
        String nombrePolitica = politica != null ? politica.getNombre() : "Política";

        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", "TAREA_ASIGNADA");
        evento.put("ejecucionId", ejecucion.getId());
        evento.put("tramiteId", tramite.getId());
        evento.put("nombreNodo", nodo.getNombre());
        evento.put("nombrePolitica", nombrePolitica);
        evento.put("prioridad", tramite.getPrioridad());

        notificacionService.notificarUsuario(ejecucion.getFuncionarioId(), evento);

        notificacionService.crearNotificacion(
                ejecucion.getFuncionarioId(),
                tramite.getId(),
                nodo.getId(),
                "TAREA_ASIGNADA",
                "Se te asignó una tarea del trámite '" + tramite.getTitulo() + "'."
        );

        // Push notification al funcionario asignado
        usuarioRepository.findById(ejecucion.getFuncionarioId()).ifPresent(u -> {
            if (u.getFcmToken() != null && !u.getFcmToken().isBlank()) {
                pushNotificacionService.enviarPush(
                    u.getFcmToken(),
                    "Nueva tarea: " + nodo.getNombre(),
                    "Trámite: " + tramite.getTitulo(),
                    Map.of("tipo", "ASIGNACION", "ejecucionId", ejecucion.getId(), "tramiteId", tramite.getId())
                );
            }
        });
    }

    private AsignacionUsuario buscarFuncionarioAsignado(String empresaId, String departamentoId) {
        List<Usuario> funcionarios = usuarioRepository.findByDepartamentoIdAndActivoTrue(departamentoId).stream()
                .filter(u -> "FUNCIONARIO".equals(u.getRol()))
                .collect(Collectors.toList());

        if (!funcionarios.isEmpty()) {
            Usuario seleccionado = funcionarios.stream()
                    .min((a, b) -> Long.compare(
                            ejecucionNodoRepository.countByFuncionarioIdAndEstadoIn(a.getId(), ESTADOS_ACTIVOS_FUNCIONARIO),
                            ejecucionNodoRepository.countByFuncionarioIdAndEstadoIn(b.getId(), ESTADOS_ACTIVOS_FUNCIONARIO)
                    ))
                    .orElse(funcionarios.get(0));
            return new AsignacionUsuario(seleccionado.getId(), "PENDIENTE");
        }

        Departamento depto = departamentoRepository.findById(departamentoId).orElse(null);
        if (depto != null && depto.getAdminDepartamentoId() != null && !depto.getAdminDepartamentoId().isBlank()) {
            return new AsignacionUsuario(depto.getAdminDepartamentoId(), "PENDIENTE");
        }

        return new AsignacionUsuario(null, "PENDIENTE_SIN_ASIGNAR");
    }

    private void notificarAdminsGenerales(String empresaId, String tramiteId, String nodoId, String tipo, String mensaje) {
        if (empresaId == null || empresaId.isBlank()) {
            return;
        }

        List<Usuario> admins = usuarioRepository.findByEmpresaIdAndRolAndActivoTrue(empresaId, "ADMIN_GENERAL");
        for (Usuario admin : admins) {
            notificacionService.crearNotificacion(admin.getId(), tramiteId, nodoId, tipo, mensaje);
        }
    }

    private String extraerValorDecision(EjecucionNodo ejecucionAnterior) {
        String valorDecision = "";
        Map<String, Object> respuesta = ejecucionAnterior.getRespuestaFormulario();
        Formulario formulario = formularioRepository.findByNodoIdAndActivoTrue(ejecucionAnterior.getNodoId()).orElse(null);

        if (formulario != null && respuesta != null) {
            for (CampoFormulario campo : formulario.getCampos()) {
                if (Boolean.TRUE.equals(campo.getEsCampoPrioridad())) {
                    Object valor = respuesta.get(campo.getNombre());
                    valorDecision = valor != null ? String.valueOf(valor) : "";
                    break;
                }
            }
        }

        if ((valorDecision == null || valorDecision.isBlank()) && respuesta != null) {
            Object decisionDirecta = respuesta.get("decision");
            if (decisionDirecta != null) {
                valorDecision = String.valueOf(decisionDirecta);
            } else {
                Object primera = respuesta.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
                if (primera != null) {
                    valorDecision = String.valueOf(primera);
                }
            }
        }

        return valorDecision == null ? "" : valorDecision;
    }

    private List<Transicion> salientesActivas(String nodoOrigenId) {
        return transicionRepository.findByNodoOrigenId(nodoOrigenId).stream()
                .filter(t -> t.getActivo() == null || Boolean.TRUE.equals(t.getActivo()))
                .collect(Collectors.toList());
    }

    private boolean esForkParalelo(List<Transicion> salientes) {
        return salientes.size() > 1 && salientes.stream().allMatch(t -> "PARALELA".equals(t.getTipo()));
    }

    private Transicion seleccionarTransicionLineal(List<Transicion> salientes) {
        return salientes.stream()
                .filter(t -> "LINEAL".equals(t.getTipo()))
                .findFirst()
                .orElse(salientes.get(0));
    }

    private void emitirEventoNodoCompletado(Tramite tramite, String nodoAnteriorId, String nodoActualId) {
        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", "NODO_COMPLETADO");
        evento.put("tramiteId", tramite.getId());
        evento.put("nodoAnteriorId", nodoAnteriorId);
        evento.put("nodoActualId", nodoActualId);
        evento.put("estado", tramite.getEstadoGeneral());
        log.info("Emitiendo evento WebSocket NODO_COMPLETADO al canal /topic/politica/{}: tramite={}, nodoAnterior={}, nodoActual={}",
                tramite.getPoliticaId(), tramite.getId(), nodoAnteriorId, nodoActualId);
        notificacionService.notificarCambioMonitor(tramite.getPoliticaId(), evento);
    }

    private void marcarTramiteComoCompletado(Tramite tramite) {
        tramite.setEstadoGeneral("COMPLETADO");
        tramite.setFinalizadoEn(LocalDateTime.now());
        tramiteRepository.save(tramite);

        notificacionService.crearNotificacion(
                tramite.getIniciadoPor(),
                tramite.getId(),
                tramite.getNodoActualId(),
                "COMPLETADO",
                "Tu trámite '" + tramite.getTitulo() + "' ha sido completado."
        );

        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", "TRAMITE_COMPLETADO");
        evento.put("tramiteId", tramite.getId());
        evento.put("titulo", tramite.getTitulo());
        log.info("Emitiendo evento WebSocket TRAMITE_COMPLETADO al canal /topic/politica/{}: tramiteId={}",
                tramite.getPoliticaId(), tramite.getId());
        notificacionService.notificarCambioMonitor(tramite.getPoliticaId(), evento);

        // Push al Admin General
        List<Usuario> admins = usuarioRepository.findByEmpresaIdAndRolAndActivoTrue(tramite.getEmpresaId(), "ADMIN_GENERAL");
        admins.stream().filter(a -> a.getFcmToken() != null && !a.getFcmToken().isBlank()).forEach(a ->
            pushNotificacionService.enviarPush(
                a.getFcmToken(),
                "Trámite completado: " + tramite.getTitulo(),
                "El proceso ha finalizado exitosamente.",
                Map.of("tipo", "COMPLETADO", "tramiteId", tramite.getId())
            )
        );
    }

    private record AsignacionUsuario(String funcionarioId, String estado) {
    }
}
