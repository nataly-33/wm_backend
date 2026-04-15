package com.workflow.transicion.service;

import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.transicion.dto.CrearTransicionRequest;
import com.workflow.transicion.dto.TransicionResponse;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransicionService {
    private final TransicionRepository transicionRepository;
    private final PoliticaRepository politicaRepository;

    private Politica validarPoliticaEmpresa(String empresaId, String politicaId) {
        return politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
    }

    public TransicionResponse crear(String empresaId, CrearTransicionRequest request) {
        validarPoliticaEmpresa(empresaId, request.getPoliticaId());
        Transicion transicion = Transicion.builder()
                .politicaId(request.getPoliticaId())
                .nodoOrigenId(request.getNodoOrigenId())
                .nodoDestinoId(request.getNodoDestinoId())
                .tipo(request.getTipo())
                .condicion(request.getCondicion())
                .etiqueta(request.getEtiqueta())
                .activo(true)
                .build();
        return TransicionResponse.fromEntity(transicionRepository.save(transicion));
    }

    public List<TransicionResponse> listarPorPolitica(String empresaId, String politicaId) {
        validarPoliticaEmpresa(empresaId, politicaId);
        return transicionRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                .map(TransicionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public TransicionResponse actualizar(String empresaId, String id, CrearTransicionRequest request) {
        validarPoliticaEmpresa(empresaId, request.getPoliticaId());
        Transicion transicion = transicionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RuntimeException("Transicion no encontrada"));
        transicion.setNodoOrigenId(request.getNodoOrigenId());
        transicion.setNodoDestinoId(request.getNodoDestinoId());
        transicion.setTipo(request.getTipo());
        transicion.setCondicion(request.getCondicion());
        transicion.setEtiqueta(request.getEtiqueta());
        return TransicionResponse.fromEntity(transicionRepository.save(transicion));
    }

    public void eliminar(String empresaId, String id) {
        Transicion transicion = transicionRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RuntimeException("Transicion no encontrada"));
        validarPoliticaEmpresa(empresaId, transicion.getPoliticaId());
        transicion.setActivo(false);
        transicionRepository.save(transicion);
    }
}
