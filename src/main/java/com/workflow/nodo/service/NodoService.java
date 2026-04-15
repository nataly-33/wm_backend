package com.workflow.nodo.service;

import com.workflow.nodo.dto.ActualizarPosicionNodoRequest;
import com.workflow.nodo.dto.CrearNodoRequest;
import com.workflow.nodo.dto.NodoResponse;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodoService {
    private final NodoRepository nodoRepository;
    private final PoliticaRepository politicaRepository;

    private Politica validarPoliticaEmpresa(String empresaId, String politicaId) {
        return politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
    }

    public NodoResponse crear(String empresaId, CrearNodoRequest request) {
        validarPoliticaEmpresa(empresaId, request.getPoliticaId());
        Nodo nodo = Nodo.builder()
                .politicaId(request.getPoliticaId())
                .departamentoId(request.getDepartamentoId())
                .nombre(request.getNombre())
                .tipo(request.getTipo())
                .posicionX(request.getPosicionX() != null ? request.getPosicionX() : 100D)
                .posicionY(request.getPosicionY() != null ? request.getPosicionY() : 100D)
                .formularioId(request.getFormularioId())
                .activo(true)
                .build();
        return NodoResponse.fromEntity(nodoRepository.save(nodo));
    }

    public List<NodoResponse> listarPorPolitica(String empresaId, String politicaId) {
        validarPoliticaEmpresa(empresaId, politicaId);
        return nodoRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                .map(NodoResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public NodoResponse actualizar(String empresaId, String nodoId, CrearNodoRequest request) {
        validarPoliticaEmpresa(empresaId, request.getPoliticaId());
        Nodo nodo = nodoRepository.findByIdAndActivoTrue(nodoId)
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado"));
        if (!nodo.getPoliticaId().equals(request.getPoliticaId())) {
            throw new RuntimeException("El nodo no pertenece a la politica indicada");
        }
        nodo.setDepartamentoId(request.getDepartamentoId());
        nodo.setNombre(request.getNombre());
        nodo.setTipo(request.getTipo());
        nodo.setPosicionX(request.getPosicionX());
        nodo.setPosicionY(request.getPosicionY());
        nodo.setFormularioId(request.getFormularioId());
        return NodoResponse.fromEntity(nodoRepository.save(nodo));
    }

    public NodoResponse actualizarPosicion(String empresaId, String nodoId, ActualizarPosicionNodoRequest request) {
        Nodo nodo = nodoRepository.findByIdAndActivoTrue(nodoId)
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado"));
        validarPoliticaEmpresa(empresaId, nodo.getPoliticaId());
        nodo.setPosicionX(request.getPosicionX());
        nodo.setPosicionY(request.getPosicionY());
        return NodoResponse.fromEntity(nodoRepository.save(nodo));
    }

    public void eliminar(String empresaId, String nodoId) {
        Nodo nodo = nodoRepository.findByIdAndActivoTrue(nodoId)
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado"));
        validarPoliticaEmpresa(empresaId, nodo.getPoliticaId());
        nodo.setActivo(false);
        nodoRepository.save(nodo);
    }
}
