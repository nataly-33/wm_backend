package com.workflow.politica.service;

import com.workflow.politica.dto.CrearPoliticaRequest;
import com.workflow.politica.dto.DiagramaResponse;
import com.workflow.politica.dto.GuardarDiagramaRequest;
import com.workflow.politica.dto.PoliticaResponse;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.departamento.dto.DepartamentoResponse;
import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.nodo.dto.NodoResponse;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.transicion.dto.TransicionResponse;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PoliticaService {
    private static final Set<String> TIPOS_NODO_VALIDOS = Set.of("INICIO", "TAREA", "DECISION", "PARALELO", "FIN");
    private static final Set<String> TIPOS_TRANSICION_VALIDOS = Set.of("LINEAL", "ALTERNATIVA", "PARALELA");

    private final PoliticaRepository politicaRepository;
    private final NodoRepository nodoRepository;
    private final TransicionRepository transicionRepository;
    private final DepartamentoRepository departamentoRepository;

    public PoliticaResponse crear(String empresaId, String userId, CrearPoliticaRequest request) {
        Politica politica = Politica.builder()
                .empresaId(empresaId)
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .version(1)
                .estado("BORRADOR")
                .generadaPorIa(false)
                .creadoPor(userId)
                .activo(true)
                .build();
        return PoliticaResponse.fromEntity(politicaRepository.save(politica));
    }

    public List<PoliticaResponse> listarPorEmpresa(String empresaId) {
        return politicaRepository.findByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(PoliticaResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public PoliticaResponse obtener(String empresaId, String politicaId) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
        return PoliticaResponse.fromEntity(politica);
    }

    public PoliticaResponse actualizar(String empresaId, String politicaId, CrearPoliticaRequest request) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
        if ("ACTIVA".equals(politica.getEstado())) {
            throw new RuntimeException("No se puede editar una politica activa");
        }
        politica.setNombre(request.getNombre());
        politica.setDescripcion(request.getDescripcion());
        return PoliticaResponse.fromEntity(politicaRepository.save(politica));
    }

    public void eliminar(String empresaId, String politicaId) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
        politica.setActivo(false);
        politicaRepository.save(politica);
    }

    public PoliticaResponse activar(String empresaId, String politicaId) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
        validarDiagramaParaActivacion(empresaId, politicaId);
        politica.setEstado("ACTIVA");
        return PoliticaResponse.fromEntity(politicaRepository.save(politica));
    }

    private void validarDiagramaParaActivacion(String empresaId, String politicaId) {
        List<Nodo> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        if (nodos.isEmpty()) {
            throw new RuntimeException("No hay nodos definidos en el diagrama");
        }
        long inicios = nodos.stream().filter(n -> "INICIO".equals(n.getTipo())).count();
        if (inicios != 1) {
            throw new RuntimeException("El diagrama debe tener exactamente un nodo de Inicio");
        }
        long fines = nodos.stream().filter(n -> "FIN".equals(n.getTipo())).count();
        if (fines < 1) {
            throw new RuntimeException("El diagrama debe tener al menos un nodo Fin");
        }
        long tareas = nodos.stream().filter(n -> "TAREA".equals(n.getTipo())).count();
        if (tareas < 1) {
            throw new RuntimeException("El diagrama debe tener al menos una tarea");
        }

        List<Transicion> transiciones = transicionRepository.findByPoliticaIdAndActivoTrue(politicaId);
        Map<String, List<Transicion>> porOrigen = transiciones.stream()
                .collect(Collectors.groupingBy(Transicion::getNodoOrigenId));

        Nodo inicio = nodos.stream().filter(n -> "INICIO".equals(n.getTipo())).findFirst().orElseThrow();
        Set<String> finIds = nodos.stream().filter(n -> "FIN".equals(n.getTipo())).map(Nodo::getId).collect(Collectors.toSet());

        if (!existeCaminoHastaFin(inicio.getId(), finIds, porOrigen)) {
            throw new RuntimeException("No existe un camino valido desde Inicio hasta Fin");
        }

        for (Nodo n : nodos) {
            String tipo = n.getTipo();
            if ("INICIO".equals(tipo) || "FIN".equals(tipo)) {
                continue;
            }
            String depId = n.getDepartamentoId();
            if (depId == null || depId.isBlank()) {
                throw new RuntimeException("El nodo '" + n.getNombre() + "' debe tener departamento asignado");
            }
            Departamento d = departamentoRepository.findByIdAndEmpresaId(depId, empresaId)
                    .orElseThrow(() -> new RuntimeException("Departamento no encontrado para el nodo '" + n.getNombre() + "'"));
            if (d.getAdminDepartamentoId() == null || d.getAdminDepartamentoId().isBlank()) {
                throw new RuntimeException("El departamento '" + d.getNombre()
                        + "' no tiene administrador asignado (requerido para activar la politica)");
            }
        }

        for (Nodo n : nodos) {
            if (!"DECISION".equals(n.getTipo())) {
                continue;
            }
            List<Transicion> outs = porOrigen.getOrDefault(n.getId(), List.of());
            List<Transicion> alternativas = outs.stream().filter(t -> "ALTERNATIVA".equals(t.getTipo())).collect(Collectors.toList());
            if (alternativas.size() < 2) {
                throw new RuntimeException("El nodo de decision '" + n.getNombre()
                        + "' debe tener al menos 2 transiciones de tipo ALTERNATIVA");
            }
            for (Transicion t : alternativas) {
                if (t.getEtiqueta() == null || t.getEtiqueta().isBlank()) {
                    throw new RuntimeException("Toda transicion ALTERNATIVA desde '" + n.getNombre() + "' debe tener etiqueta");
                }
            }
        }
    }

    private boolean existeCaminoHastaFin(String inicioId, Set<String> finIds, Map<String, List<Transicion>> porOrigen) {
        if (finIds.contains(inicioId)) {
            return true;
        }
        Queue<String> q = new ArrayDeque<>();
        Set<String> vis = new HashSet<>();
        q.add(inicioId);
        vis.add(inicioId);
        while (!q.isEmpty()) {
            String u = q.poll();
            for (Transicion t : porOrigen.getOrDefault(u, List.of())) {
                String v = t.getNodoDestinoId();
                if (finIds.contains(v)) {
                    return true;
                }
                if (vis.add(v)) {
                    q.add(v);
                }
            }
        }
        return false;
    }

    public PoliticaResponse desactivar(String empresaId, String politicaId) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
        politica.setEstado("INACTIVA");
        return PoliticaResponse.fromEntity(politicaRepository.save(politica));
    }

    public PoliticaResponse guardarDiagrama(String empresaId, String userId, String politicaId, GuardarDiagramaRequest request) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
                
        if ("ACTIVA".equals(politica.getEstado())) {
            throw new RuntimeException("No se puede editar el diagrama de una politica activa");
        }

        validarRequestGuardarDiagrama(empresaId, request);

        List<Nodo> nodosExistentes = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        List<Transicion> transicionesExistentes = transicionRepository.findByPoliticaIdAndActivoTrue(politicaId);
        Map<String, Nodo> nodoExistentePorId = nodosExistentes.stream()
                .collect(Collectors.toMap(Nodo::getId, n -> n));
        Map<String, Transicion> transicionExistentePorId = transicionesExistentes.stream()
                .collect(Collectors.toMap(Transicion::getId, t -> t));

        Map<String, String> tempIdToRealId = new HashMap<>();
        Set<String> nodosPresentes = new HashSet<>();
        List<GuardarDiagramaRequest.NodoDiagramaPayload> nodosEntrada =
                request.getNodos() == null ? List.of() : request.getNodos();
        for (GuardarDiagramaRequest.NodoDiagramaPayload payload : nodosEntrada) {
            Nodo nodo;
            if (payload.getId() != null) {
                nodo = nodoExistentePorId.get(payload.getId());
                if (nodo == null) {
                    throw new RuntimeException("Nodo referenciado no existe en esta politica: " + payload.getId());
                }
            } else {
                nodo = new Nodo();
            }

            if (nodoRequiereDepartamento(payload.getTipo())) {
                if (payload.getDepartamentoId() == null || payload.getDepartamentoId().isBlank()) {
                    throw new RuntimeException("El nodo '" + payload.getNombre() + "' requiere departamento");
                }
                departamentoRepository.findByIdAndEmpresaId(payload.getDepartamentoId(), empresaId)
                        .orElseThrow(() -> new RuntimeException("Departamento no encontrado para nodo '" + payload.getNombre() + "'"));
            }

            nodo.setPoliticaId(politicaId);
            nodo.setDepartamentoId(payload.getDepartamentoId());
            nodo.setNombre(payload.getNombre());
            nodo.setTipo(payload.getTipo());
            nodo.setPosicionX(payload.getPosicionX());
            nodo.setPosicionY(payload.getPosicionY());
            nodo.setFormularioId(payload.getFormularioId());
            nodo.setActivo(true);
            Nodo guardado = nodoRepository.save(nodo);
            nodosPresentes.add(guardado.getId());
            String claveTemp = payload.getTempId() != null ? payload.getTempId() : guardado.getId();
            tempIdToRealId.put(claveTemp, guardado.getId());
            tempIdToRealId.put(guardado.getId(), guardado.getId());
        }

        for (Nodo nodo : nodosExistentes) {
            if (!nodosPresentes.contains(nodo.getId())) {
                nodo.setActivo(false);
                nodoRepository.save(nodo);
            }
        }

        Set<String> transicionesPresentes = new HashSet<>();
        List<GuardarDiagramaRequest.TransicionDiagramaPayload> transicionesEntrada =
                request.getTransiciones() == null ? List.of() : request.getTransiciones();
        for (GuardarDiagramaRequest.TransicionDiagramaPayload payload : transicionesEntrada) {
            String origen = tempIdToRealId.get(payload.getNodoOrigenTempId());
            String destino = tempIdToRealId.get(payload.getNodoDestinoTempId());
            if (origen == null || destino == null) {
                throw new RuntimeException("Transicion con nodos no resueltos");
            }

            Transicion transicion;
            if (payload.getId() != null) {
                transicion = transicionExistentePorId.get(payload.getId());
                if (transicion == null) {
                    throw new RuntimeException("Transicion referenciada no existe en esta politica: " + payload.getId());
                }
            } else {
                transicion = new Transicion();
            }

            transicion.setPoliticaId(politicaId);
            transicion.setNodoOrigenId(origen);
            transicion.setNodoDestinoId(destino);
            transicion.setTipo(payload.getTipo());
            transicion.setEtiqueta(payload.getEtiqueta());
            transicion.setCondicion(payload.getCondicion());
            transicion.setActivo(true);
            Transicion guardada = transicionRepository.save(transicion);
            transicionesPresentes.add(guardada.getId());
        }

        for (Transicion t : transicionesExistentes) {
            if (!transicionesPresentes.contains(t.getId())) {
                t.setActivo(false);
                transicionRepository.save(t);
            }
        }

        politica.setDatosDiagramaJson(request.getDatosDiagramaJson());
        politica.setVersion((politica.getVersion() == null ? 0 : politica.getVersion()) + 1);
        Politica guardada = politicaRepository.save(politica);
        return PoliticaResponse.fromEntity(guardada);
    }

    public DiagramaResponse obtenerDiagrama(String empresaId, String politicaId) {
        Politica politica = politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));

        List<NodoResponse> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                .map(NodoResponse::fromEntity)
                .collect(Collectors.toList());
        List<TransicionResponse> transiciones = transicionRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                .map(TransicionResponse::fromEntity)
                .collect(Collectors.toList());
        List<DepartamentoResponse> departamentos = departamentoRepository.findByEmpresaIdAndActivo(empresaId, true).stream()
            .filter(d -> d.getAdminDepartamentoId() != null && !d.getAdminDepartamentoId().isBlank())
                .map(DepartamentoResponse::fromEntity)
                .collect(Collectors.toList());

        return new DiagramaResponse(
                politica.getDatosDiagramaJson(),
                nodos,
                transiciones,
                departamentos
        );
    }

    private void validarRequestGuardarDiagrama(String empresaId, GuardarDiagramaRequest request) {
        if (request == null) {
            throw new RuntimeException("Request de diagrama requerido");
        }

        if (request.getDatosDiagramaJson() == null || request.getDatosDiagramaJson().isBlank()) {
            throw new RuntimeException("datosDiagramaJson es requerido para guardar el diagrama");
        }

        List<GuardarDiagramaRequest.NodoDiagramaPayload> nodosEntrada =
                request.getNodos() == null ? List.of() : request.getNodos();
        List<GuardarDiagramaRequest.TransicionDiagramaPayload> transicionesEntrada =
                request.getTransiciones() == null ? List.of() : request.getTransiciones();

        Set<String> idsTemporales = new HashSet<>();
        long inicios = 0;

        for (GuardarDiagramaRequest.NodoDiagramaPayload nodo : nodosEntrada) {
            if (nodo.getTipo() == null || !TIPOS_NODO_VALIDOS.contains(nodo.getTipo())) {
                throw new RuntimeException("Tipo de nodo invalido: " + nodo.getTipo());
            }

            if ("INICIO".equals(nodo.getTipo())) {
                inicios++;
            }

            if (nodoRequiereDepartamento(nodo.getTipo())) {
                if (nodo.getDepartamentoId() == null || nodo.getDepartamentoId().isBlank()) {
                    throw new RuntimeException("Nodo '" + nodo.getNombre() + "' sin departamento asignado");
                }
                departamentoRepository.findByIdAndEmpresaId(nodo.getDepartamentoId(), empresaId)
                        .orElseThrow(() -> new RuntimeException("Departamento no encontrado para nodo '" + nodo.getNombre() + "'"));
            }

            String ref = obtenerRefNodo(nodo);
            if (!idsTemporales.add(ref)) {
                throw new RuntimeException("Nodo duplicado en request: " + ref);
            }
        }

        if (inicios > 1) {
            throw new RuntimeException("El diagrama no puede tener mas de un nodo INICIO");
        }

        for (GuardarDiagramaRequest.TransicionDiagramaPayload tr : transicionesEntrada) {
            if (tr.getTipo() == null || !TIPOS_TRANSICION_VALIDOS.contains(tr.getTipo())) {
                throw new RuntimeException("Tipo de transicion invalido: " + tr.getTipo());
            }

            if (tr.getNodoOrigenTempId() == null || tr.getNodoOrigenTempId().isBlank()
                    || tr.getNodoDestinoTempId() == null || tr.getNodoDestinoTempId().isBlank()) {
                throw new RuntimeException("Toda transicion debe tener origen y destino");
            }

            if (tr.getNodoOrigenTempId().equals(tr.getNodoDestinoTempId())) {
                throw new RuntimeException("No se permiten transiciones de un nodo hacia si mismo");
            }

            if (!idsTemporales.contains(tr.getNodoOrigenTempId()) || !idsTemporales.contains(tr.getNodoDestinoTempId())) {
                throw new RuntimeException("Transicion referencia nodos inexistentes en el payload");
            }
        }
    }

    private String obtenerRefNodo(GuardarDiagramaRequest.NodoDiagramaPayload nodo) {
        if (nodo.getTempId() != null && !nodo.getTempId().isBlank()) {
            return nodo.getTempId();
        }
        if (nodo.getId() != null && !nodo.getId().isBlank()) {
            return nodo.getId();
        }
        throw new RuntimeException("Nodo sin id ni tempId");
    }

    private boolean nodoRequiereDepartamento(String tipo) {
        return !"INICIO".equals(tipo) && !"FIN".equals(tipo);
    }
}
