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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PoliticaService {
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
        politica.setEstado("ACTIVA");
        return PoliticaResponse.fromEntity(politicaRepository.save(politica));
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

        List<Nodo> nodosExistentes = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        List<Transicion> transicionesExistentes = transicionRepository.findByPoliticaIdAndActivoTrue(politicaId);

        Map<String, String> tempIdToRealId = new HashMap<>();
        Set<String> nodosPresentes = new HashSet<>();
        List<GuardarDiagramaRequest.NodoDiagramaPayload> nodosEntrada =
                request.getNodos() == null ? List.of() : request.getNodos();
        for (GuardarDiagramaRequest.NodoDiagramaPayload payload : nodosEntrada) {
            Nodo nodo = nodosExistentes.stream()
                    .filter(n -> payload.getId() != null && payload.getId().equals(n.getId()))
                    .findFirst()
                    .orElseGet(Nodo::new);

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

            Transicion transicion = transicionesExistentes.stream()
                    .filter(t -> payload.getId() != null && payload.getId().equals(t.getId()))
                    .findFirst()
                    .orElseGet(Transicion::new);

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
        politica.setVersion(politica.getVersion() + 1);
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
        List<DepartamentoResponse> departamentos = departamentoRepository.findByEmpresaIdAndAdminDepartamentoIdIsNotNullAndActivoTrue(empresaId)
                .stream()
                .map(DepartamentoResponse::fromEntity)
                .collect(Collectors.toList());

        return new DiagramaResponse(
                politica.getDatosDiagramaJson(),
                nodos,
                transiciones,
                departamentos
        );
    }
}
