package com.workflow.politica.service;

import com.workflow.politica.dto.CrearPoliticaRequest;
import com.workflow.politica.dto.PoliticaResponse;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PoliticaService {
    private final PoliticaRepository politicaRepository;

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
}
