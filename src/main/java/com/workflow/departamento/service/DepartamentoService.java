package com.workflow.departamento.service;

import com.workflow.departamento.dto.CrearDepartamentoRequest;
import com.workflow.departamento.dto.DepartamentoResponse;
import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartamentoService {
    private final DepartamentoRepository departamentoRepository;

    public DepartamentoResponse crearDepartamento(String empresaId, CrearDepartamentoRequest request) {
        Departamento depto = new Departamento();
        depto.setEmpresaId(empresaId);
        depto.setNombre(request.getNombre());
        depto.setDescripcion(request.getDescripcion());
        
        String adminId = request.getAdminDepartamentoId();
        depto.setAdminDepartamentoId((adminId != null && !adminId.trim().isEmpty()) ? adminId : null);
        
        depto.setActivo(true);
        depto.setCreadoEn(LocalDateTime.now());

        Departamento saved = departamentoRepository.save(depto);
        return DepartamentoResponse.fromEntity(saved);
    }

    public DepartamentoResponse actualizarDepartamento(String empresaId, String deptoId, CrearDepartamentoRequest request) {
        Departamento depto = departamentoRepository.findByIdAndEmpresaId(deptoId, empresaId)
            .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));

        depto.setNombre(request.getNombre());
        depto.setDescripcion(request.getDescripcion());
        
        String adminId = request.getAdminDepartamentoId();
        depto.setAdminDepartamentoId((adminId != null && !adminId.trim().isEmpty()) ? adminId : null);

        Departamento updated = departamentoRepository.save(depto);
        return DepartamentoResponse.fromEntity(updated);
    }

    public void eliminarDepartamento(String empresaId, String deptoId) {
        Departamento depto = departamentoRepository.findByIdAndEmpresaId(deptoId, empresaId)
            .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
        depto.setActivo(false);
        departamentoRepository.save(depto);
    }

    public DepartamentoResponse obtenerDepartamento(String empresaId, String deptoId) {
        Departamento depto = departamentoRepository.findByIdAndEmpresaId(deptoId, empresaId)
            .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
        return DepartamentoResponse.fromEntity(depto);
    }

    public List<DepartamentoResponse> listarDepartamentos(String empresaId) {
        return departamentoRepository.findByEmpresaIdAndActivo(empresaId, true).stream()
            .map(DepartamentoResponse::fromEntity)
            .collect(Collectors.toList());
    }

    public List<DepartamentoResponse> listarDepartamentosSinAdmin(String empresaId) {
        return departamentoRepository.findByEmpresaIdAndAdminDepartamentoIdIsNullAndActivoTrue(empresaId).stream()
            .map(DepartamentoResponse::fromEntity)
            .collect(Collectors.toList());
    }

    public List<DepartamentoResponse> listarDepartamentosCompletos(String empresaId) {
        return departamentoRepository.findByEmpresaIdAndAdminDepartamentoIdIsNotNullAndActivoTrue(empresaId).stream()
            .map(DepartamentoResponse::fromEntity)
            .collect(Collectors.toList());
    }
}
