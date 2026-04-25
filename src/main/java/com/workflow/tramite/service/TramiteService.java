package com.workflow.tramite.service;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.dto.TramiteDetalladoResponse;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TramiteService {
    private final TramiteRepository tramiteRepository;
    private final MotorWorkflowService motorWorkflowService;
    private final NodoRepository nodoRepository;
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final DepartamentoRepository departamentoRepository;
    private final PoliticaRepository politicaRepository;
    private final UsuarioRepository usuarioRepository;

    // ─── Iniciar trámite ─────────────────────────────────────────────────────────

    public Tramite iniciarTramite(Map<String, Object> body, String iniciadoPor, String empresaId) {
        String politicaId = (String) body.get("politicaId");
        String titulo = (String) body.get("titulo");
        String prioridad = (String) body.get("prioridad");

        Nodo nodoInicio = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                .filter(n -> "INICIO".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("La política no tiene un nodo de tipo INICIO"));

        Tramite tramite = Tramite.builder()
                .politicaId(politicaId)
                .empresaId(empresaId)
                .titulo(titulo)
                .prioridad(prioridad != null ? prioridad : "MEDIA")
                .estadoGeneral("PENDIENTE")
                .iniciadoPor(iniciadoPor)
                .nodosParalelosPendientes(new ArrayList<>())
                .iteracionesPorNodo(new HashMap<>())
                .build();

        return motorWorkflowService.iniciarTramite(tramite, nodoInicio.getId());
    }

    // ─── Listados básicos ─────────────────────────────────────────────────────────

    public List<Tramite> listarTramitesEmpresa(String empresaId) {
        return tramiteRepository.findByEmpresaId(empresaId);
    }

    public Tramite obtenerTramite(String id) {
        return tramiteRepository.findById(id).orElseThrow(() -> new RuntimeException("Trámite no encontrado"));
    }

    public List<Tramite> listarTramitesPolitica(String politicaId) {
        return tramiteRepository.findByPoliticaId(politicaId);
    }

    public List<Tramite> listarTramitesDepartamento(String departamentoId) {
        List<EjecucionNodo> ejecuciones = ejecucionNodoRepository.findByDepartamentoId(departamentoId);
        List<String> tramiteIds = ejecuciones.stream()
                .map(EjecucionNodo::getTramiteId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (tramiteIds.isEmpty()) {
            return List.of();
        }
        return tramiteRepository.findByIdIn(tramiteIds).stream()
                .sorted(Comparator.comparing(Tramite::getIniciadoEn,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public Map<String, Object> obtenerTramiteConEjecuciones(String id) {
        Tramite tramite = obtenerTramite(id);
        List<EjecucionNodo> ejecuciones = ejecucionNodoRepository.findByTramiteIdOrderByIniciadoEnDesc(id);
        return Map.of("tramite", tramite, "ejecuciones", ejecuciones);
    }

    public Tramite cancelarTramite(String id) {
        Tramite tramite = obtenerTramite(id);
        if (Set.of("COMPLETADO", "RECHAZADO").contains(tramite.getEstadoGeneral())) {
            throw new RuntimeException("No se puede cancelar un trámite ya finalizado");
        }
        tramite.setEstadoGeneral("RECHAZADO");
        tramite.setFinalizadoEn(LocalDateTime.now());
        return tramiteRepository.save(tramite);
    }

    // ─── Trámites enriquecidos (dashboard) ───────────────────────────────────────

    public List<TramiteDetalladoResponse> listarTramitesEmpresaEnriquecidos(String empresaId) {
        List<Tramite> tramites = tramiteRepository.findByEmpresaId(empresaId);

        Map<String, String> cachePolitica = new HashMap<>();
        Map<String, String> cacheDepto = new HashMap<>();
        Map<String, String> cacheUser = new HashMap<>();

        List<TramiteDetalladoResponse> resultado = new ArrayList<>();

        for (Tramite t : tramites) {
            TramiteDetalladoResponse.TramiteDetalladoResponseBuilder builder =
                    TramiteDetalladoResponse.builder()
                            .id(t.getId())
                            .titulo(t.getTitulo())
                            .estadoGeneral(t.getEstadoGeneral())
                            .prioridad(t.getPrioridad() != null ? t.getPrioridad() : "MEDIA")
                            .iniciadoEn(t.getIniciadoEn())
                            .finalizadoEn(t.getFinalizadoEn())
                            .fechaLimite(t.getFechaLimite());

            // Nombre política
            if (t.getPoliticaId() != null) {
                String nombrePol = cachePolitica.computeIfAbsent(t.getPoliticaId(),
                        pid -> politicaRepository.findById(pid)
                                .map(Politica::getNombre).orElse(pid));
                builder.nombrePolitica(nombrePol);
            }

            // Tiempos
            if (t.getFinalizadoEn() != null && t.getIniciadoEn() != null) {
                builder.duracionTotal(calcularDuracion(t.getIniciadoEn(), t.getFinalizadoEn()));
            } else if (t.getIniciadoEn() != null) {
                builder.tiempoTranscurrido(calcularTiempoTranscurrido(t.getIniciadoEn()));
            }

            // Nodo actual y departamento (EN_PROCESO / PENDIENTE)
            if (t.getNodoActualId() != null) {
                nodoRepository.findById(t.getNodoActualId()).ifPresent(nodo -> {
                    builder.nodoActualNombre(nodo.getNombre());
                    if (nodo.getDepartamentoId() != null) {
                        String deptoNombre = cacheDepto.computeIfAbsent(nodo.getDepartamentoId(),
                                id -> departamentoRepository.findById(id)
                                        .map(Departamento::getNombre).orElse(id));
                        builder.departamentoActualNombre(deptoNombre);
                    }
                });
            }

            // Funcionario actual (buscar en ejecuciones activas)
            if ("EN_PROCESO".equals(t.getEstadoGeneral()) || "PENDIENTE".equals(t.getEstadoGeneral())) {
                ejecucionNodoRepository.findByTramiteId(t.getId()).stream()
                        .filter(e -> "PENDIENTE".equals(e.getEstado()) || "EN_PROCESO".equals(e.getEstado()))
                        .findFirst()
                        .ifPresent(e -> {
                            if (e.getFuncionarioId() != null) {
                                String nombre = cacheUser.computeIfAbsent(e.getFuncionarioId(),
                                        uid -> usuarioRepository.findById(uid)
                                                .map(u -> u.getNombre()).orElse("Sin asignar"));
                                builder.funcionarioActualNombre(nombre);
                            } else {
                                builder.funcionarioActualNombre("Sin asignar");
                            }
                        });
            }

            // Motivo de rechazo
            if ("RECHAZADO".equals(t.getEstadoGeneral())) {
                ejecucionNodoRepository.findByTramiteId(t.getId()).stream()
                        .filter(e -> "RECHAZADO".equals(e.getEstado()))
                        .findFirst()
                        .ifPresent(e -> {
                            builder.motivoRechazo(e.getObservaciones());
                            nodoRepository.findById(e.getNodoId()).ifPresent(n -> {
                                builder.nodoRechazoNombre(n.getNombre());
                                if (n.getDepartamentoId() != null) {
                                    String deptoNombre = cacheDepto.computeIfAbsent(n.getDepartamentoId(),
                                            id -> departamentoRepository.findById(id)
                                                    .map(Departamento::getNombre).orElse(id));
                                    builder.departamentoRechazoNombre(deptoNombre);
                                }
                            });
                        });
            }

            resultado.add(builder.build());
        }

        // Ordenar: EN_PROCESO → PENDIENTE → BLOQUEADO → COMPLETADO → RECHAZADO
        resultado.sort((a, b) -> {
            int oa = estadoOrden(a.getEstadoGeneral());
            int ob = estadoOrden(b.getEstadoGeneral());
            if (oa != ob) return Integer.compare(oa, ob);
            // Dentro de EN_PROCESO: por prioridad
            if (oa == 0) {
                int pa = prioridadOrden(a.getPrioridad());
                int pb = prioridadOrden(b.getPrioridad());
                if (pa != pb) return Integer.compare(pa, pb);
                // Luego por iniciadoEn DESC (más recientes primero)
                if (a.getIniciadoEn() != null && b.getIniciadoEn() != null) {
                    return b.getIniciadoEn().compareTo(a.getIniciadoEn());
                }
            }
            // Dentro de COMPLETADO: por finalizadoEn DESC
            if (oa == 3) {
                if (a.getFinalizadoEn() != null && b.getFinalizadoEn() != null) {
                    return b.getFinalizadoEn().compareTo(a.getFinalizadoEn());
                }
            }
            return 0;
        });

        return resultado;
    }

    // ─── Monitor en tiempo real ───────────────────────────────────────────────────

    public Map<String, Object> obtenerEstadoMonitor(String politicaId) {
        List<Nodo> todosNodos = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        List<Tramite> tramites = tramiteRepository.findByPoliticaId(politicaId);

        String nombrePolitica = politicaRepository.findById(politicaId)
                .map(Politica::getNombre).orElse(politicaId);

        List<Tramite> tramitesActivos = tramites.stream()
                .filter(t -> !"COMPLETADO".equals(t.getEstadoGeneral())
                        && !"RECHAZADO".equals(t.getEstadoGeneral()))
                .collect(Collectors.toList());

        // Ejecuciones PENDIENTE/EN_PROCESO de trámites activos
        List<String> idsActivos = tramitesActivos.stream()
                .map(Tramite::getId).collect(Collectors.toList());
        List<EjecucionNodo> ejecucionesActivas = idsActivos.isEmpty() ? List.of()
                : ejecucionNodoRepository.findByTramiteIdIn(idsActivos).stream()
                        .filter(e -> "PENDIENTE".equals(e.getEstado())
                                || "EN_PROCESO".equals(e.getEstado()))
                        .collect(Collectors.toList());

        Map<String, Tramite> tramitePorId = tramitesActivos.stream()
                .collect(Collectors.toMap(Tramite::getId, t -> t));
        Map<String, List<EjecucionNodo>> ejecucionesPorNodo = ejecucionesActivas.stream()
                .collect(Collectors.groupingBy(EjecucionNodo::getNodoId));

        // Caches
        Map<String, String> cacheDepto = new HashMap<>();
        Map<String, String> cacheUser = new HashMap<>();

        // Obtener departamentos únicos en orden de aparición
        List<String> deptoIds = new ArrayList<>();
        Set<String> seenDeptos = new HashSet<>();
        for (Nodo nodo : todosNodos) {
            if (nodo.getDepartamentoId() != null && seenDeptos.add(nodo.getDepartamentoId())) {
                deptoIds.add(nodo.getDepartamentoId());
            }
        }

        // Construir array de departamentos
        List<Map<String, Object>> departamentos = new ArrayList<>();
        for (String deptoId : deptoIds) {
            String nombreDepto = cacheDepto.computeIfAbsent(deptoId,
                    id -> departamentoRepository.findById(id)
                            .map(Departamento::getNombre).orElse(id));

            // Nodos de este departamento (solo TAREA y DECISION)
            List<Nodo> nodosDepto = todosNodos.stream()
                    .filter(n -> deptoId.equals(n.getDepartamentoId()))
                    .filter(n -> "TAREA".equals(n.getTipo()) || "DECISION".equals(n.getTipo()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> nodosActivos = new ArrayList<>();
            boolean tieneActivos = false;
            boolean tieneRechazados = false;

            for (Nodo nodo : nodosDepto) {
                List<EjecucionNodo> ejecNodo = ejecucionesPorNodo.getOrDefault(nodo.getId(), List.of());
                if (ejecNodo.isEmpty()) continue;

                List<Map<String, Object>> tramitesEnNodo = new ArrayList<>();
                for (EjecucionNodo ejec : ejecNodo) {
                    Tramite tram = tramitePorId.get(ejec.getTramiteId());
                    if (tram == null) continue;
                    tieneActivos = true;

                    String funcNombre = "Sin asignar";
                    if (ejec.getFuncionarioId() != null) {
                        funcNombre = cacheUser.computeIfAbsent(ejec.getFuncionarioId(),
                                uid -> usuarioRepository.findById(uid)
                                        .map(u -> u.getNombre()).orElse("Sin asignar"));
                    }

                    Map<String, Object> tramItem = new HashMap<>();
                    tramItem.put("tramiteId", tram.getId());
                    tramItem.put("titulo", tram.getTitulo());
                    tramItem.put("prioridad", tram.getPrioridad() != null ? tram.getPrioridad() : "MEDIA");
                    tramItem.put("ejecucionId", ejec.getId());
                    tramItem.put("funcionarioNombre", funcNombre);
                    tramItem.put("tiempoTranscurrido", calcularTiempoTranscurrido(ejec.getIniciadoEn()));
                    tramitesEnNodo.add(tramItem);
                }

                if (!tramitesEnNodo.isEmpty()) {
                    Map<String, Object> nodoMap = new HashMap<>();
                    nodoMap.put("nodoId", nodo.getId());
                    nodoMap.put("nombreNodo", nodo.getNombre());
                    nodoMap.put("tramitesActivos", tramitesEnNodo);
                    nodosActivos.add(nodoMap);
                }
            }

            // Verificar rechazados en este departamento
            for (Nodo nodo : nodosDepto) {
                List<EjecucionNodo> ejecDepto = ejecucionNodoRepository
                        .findByNodoIdAndEstadoIn(nodo.getId(), List.of("RECHAZADO"));
                if (!ejecDepto.isEmpty()) {
                    tieneRechazados = true;
                    break;
                }
            }

            String color;
            if (tieneRechazados && !tieneActivos) color = "ROJO";
            else if (tieneActivos) color = "AMARILLO";
            else color = "VACIO";

            Map<String, Object> deptoMap = new HashMap<>();
            deptoMap.put("departamentoId", deptoId);
            deptoMap.put("nombreDepartamento", nombreDepto);
            deptoMap.put("color", color);
            deptoMap.put("nodosActivos", nodosActivos);
            departamentos.add(deptoMap);
        }

        // Resumen de trámites activos
        List<Map<String, Object>> resumenActivos = tramitesActivos.stream()
                .map(t -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("tramiteId", t.getId());
                    item.put("titulo", t.getTitulo());
                    item.put("prioridad", t.getPrioridad() != null ? t.getPrioridad() : "MEDIA");
                    item.put("estadoGeneral", t.getEstadoGeneral());
                    if (t.getNodoActualId() != null) {
                        nodoRepository.findById(t.getNodoActualId()).ifPresent(n -> {
                            if (n.getDepartamentoId() != null) {
                                String deptoNombre = cacheDepto.computeIfAbsent(n.getDepartamentoId(),
                                        id -> departamentoRepository.findById(id)
                                                .map(Departamento::getNombre).orElse(id));
                                item.put("departamentoActualNombre", deptoNombre);
                            }
                        });
                    }
                    return item;
                })
                .collect(Collectors.toList());

        long completados = tramites.stream()
                .filter(t -> "COMPLETADO".equals(t.getEstadoGeneral())).count();
        long rechazados = tramites.stream()
                .filter(t -> "RECHAZADO".equals(t.getEstadoGeneral())).count();

        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("activos", (long) tramitesActivos.size());
        estadisticas.put("completados", completados);
        estadisticas.put("rechazados", rechazados);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("politicaId", politicaId);
        resultado.put("nombrePolitica", nombrePolitica);
        resultado.put("estadisticas", estadisticas);
        resultado.put("departamentos", departamentos);
        resultado.put("tramitesActivos", resumenActivos);
        return resultado;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String formatDuration(long minutos) {
        if (minutos < 1) return "Ahora";
        if (minutos < 60) return minutos + " min";
        long horas = minutos / 60;
        long mins = minutos % 60;
        if (mins == 0) return horas + "h";
        return horas + "h " + mins + "min";
    }

    private String calcularTiempoTranscurrido(LocalDateTime desde) {
        if (desde == null) return "Pendiente";
        long minutos = Duration.between(desde, LocalDateTime.now()).toMinutes();
        return formatDuration(Math.max(0, minutos));
    }

    private String calcularDuracion(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null || hasta == null) return null;
        long minutos = Duration.between(desde, hasta).toMinutes();
        return formatDuration(Math.max(0, minutos));
    }

    private int estadoOrden(String estado) {
        if (estado == null) return 1;
        return switch (estado) {
            case "EN_PROCESO" -> 0;
            case "PENDIENTE" -> 1;
            case "BLOQUEADO" -> 2;
            case "COMPLETADO" -> 3;
            case "RECHAZADO" -> 4;
            default -> 5;
        };
    }

    private int prioridadOrden(String prioridad) {
        if (prioridad == null) return 1;
        return switch (prioridad) {
            case "ALTA" -> 0;
            case "MEDIA" -> 1;
            case "BAJA" -> 2;
            default -> 1;
        };
    }
}
