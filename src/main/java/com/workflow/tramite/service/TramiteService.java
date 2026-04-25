package com.workflow.tramite.service;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    public Tramite iniciarTramite(Map<String, Object> body, String iniciadoPor, String empresaId) {
        String politicaId = (String) body.get("politicaId");
        String titulo = (String) body.get("titulo");
        String prioridad = (String) body.get("prioridad");

        // Buscar nodo INICIO
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
                .sorted(Comparator.comparing(Tramite::getIniciadoEn, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public Map<String, Object> obtenerTramiteConEjecuciones(String id) {
        Tramite tramite = obtenerTramite(id);
        List<EjecucionNodo> ejecuciones = ejecucionNodoRepository.findByTramiteIdOrderByIniciadoEnDesc(id);
        return Map.of(
                "tramite", tramite,
                "ejecuciones", ejecuciones
        );
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

    public Map<String, Object> obtenerEstadoMonitor(String politicaId) {
        List<Nodo> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        List<Tramite> tramites = tramiteRepository.findByPoliticaId(politicaId);

        String nombrePolitica = politicaRepository.findById(politicaId)
                .map(Politica::getNombre).orElse(politicaId);

        List<Tramite> tramitesActivos = tramites.stream()
                .filter(t -> !"COMPLETADO".equals(t.getEstadoGeneral()) && !"RECHAZADO".equals(t.getEstadoGeneral()))
                .collect(Collectors.toList());

        List<String> tramitesActivosIds = tramitesActivos.stream().map(Tramite::getId).collect(Collectors.toList());
        List<EjecucionNodo> ejecucionesActivas = tramitesActivosIds.isEmpty()
                ? List.of()
                : ejecucionNodoRepository.findByTramiteIdIn(tramitesActivosIds);

        Map<String, Tramite> tramitePorId = tramitesActivos.stream()
                .collect(Collectors.toMap(Tramite::getId, t -> t));
        Map<String, List<EjecucionNodo>> ejecucionesPorNodo = ejecucionesActivas.stream()
                .collect(Collectors.groupingBy(EjecucionNodo::getNodoId));

        // Cache de nombres de departamentos para evitar N+1 queries
        Map<String, String> cacheNombreDepto = new HashMap<>();

        List<Map<String, Object>> nodosEstado = new ArrayList<>();
        for (Nodo nodo : nodos) {
            List<EjecucionNodo> ejecucionesNodo = ejecucionesPorNodo.getOrDefault(nodo.getId(), List.of());
            String color = calcularColorNodo(ejecucionesNodo);

            String nombreDepto = null;
            if (nodo.getDepartamentoId() != null) {
                nombreDepto = cacheNombreDepto.computeIfAbsent(nodo.getDepartamentoId(),
                        depId -> departamentoRepository.findById(depId)
                                .map(Departamento::getNombre).orElse(depId));
            }

            List<Map<String, Object>> tramitesNodo = ejecucionesNodo.stream()
                    .map(e -> {
                        Tramite t = tramitePorId.get(e.getTramiteId());
                        if (t == null) return null;
                        Map<String, Object> item = new HashMap<>();
                        item.put("tramiteId", t.getId());
                        item.put("titulo", t.getTitulo());
                        item.put("prioridad", t.getPrioridad() != null ? t.getPrioridad() : "MEDIA");
                        item.put("ejecucionId", e.getId());
                        return item;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Map<String, Object> nodoMap = new HashMap<>();
            nodoMap.put("nodoId", nodo.getId());
            nodoMap.put("nombreNodo", nodo.getNombre());
            nodoMap.put("nombreDepartamento", nombreDepto);
            nodoMap.put("tipo", nodo.getTipo());
            nodoMap.put("color", color);
            nodoMap.put("tramitesActivos", tramitesNodo);
            nodosEstado.add(nodoMap);
        }

        List<Map<String, Object>> resumenActivos = tramitesActivos.stream()
            .map(t -> {
                Map<String, Object> item = new HashMap<>();
                item.put("tramiteId", t.getId());
                item.put("titulo", t.getTitulo());
                item.put("prioridad", t.getPrioridad() != null ? t.getPrioridad() : "MEDIA");
                item.put("estadoGeneral", t.getEstadoGeneral());
                item.put("iniciadoEn", t.getIniciadoEn());
                if (t.getNodoActualId() != null) {
                    nodoRepository.findById(t.getNodoActualId()).ifPresent(n -> {
                        item.put("nodoActualNombre", n.getNombre());
                        if (n.getDepartamentoId() != null) {
                            String deptoNombre = cacheNombreDepto.computeIfAbsent(n.getDepartamentoId(),
                                    depId -> departamentoRepository.findById(depId)
                                            .map(Departamento::getNombre).orElse(depId));
                            item.put("departamentoActualNombre", deptoNombre);
                        }
                    });
                }
                return item;
            })
            .collect(Collectors.toList());

        long completados = tramites.stream().filter(t -> "COMPLETADO".equals(t.getEstadoGeneral())).count();
        long rechazados = tramites.stream().filter(t -> "RECHAZADO".equals(t.getEstadoGeneral())).count();

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("politicaId", politicaId);
        resultado.put("nombrePolitica", nombrePolitica);
        resultado.put("nodos", nodosEstado);
        resultado.put("tramitesActivos", resumenActivos);
        resultado.put("tramitesCompletados", completados);
        resultado.put("tramitesRechazados", rechazados);
        return resultado;
    }

    private String calcularColorNodo(List<EjecucionNodo> ejecucionesNodo) {
        if (ejecucionesNodo == null || ejecucionesNodo.isEmpty()) {
            return "GRIS";
        }
        if (ejecucionesNodo.stream().anyMatch(e -> "RECHAZADO".equals(e.getEstado()))) {
            return "ROJO";
        }
        if (ejecucionesNodo.stream().anyMatch(e -> "PENDIENTE_SIN_ASIGNAR".equals(e.getEstado()))) {
            return "NARANJA";
        }
        if (ejecucionesNodo.stream().anyMatch(e -> "PENDIENTE".equals(e.getEstado()) || "EN_PROCESO".equals(e.getEstado()))) {
            return "AMARILLO";
        }
        if (ejecucionesNodo.stream().allMatch(e -> "COMPLETADO".equals(e.getEstado()))) {
            return "VERDE";
        }
        return "GRIS";
    }
}
