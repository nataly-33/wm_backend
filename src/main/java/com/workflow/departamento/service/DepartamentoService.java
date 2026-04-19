package com.workflow.departamento.service;

import com.workflow.departamento.dto.CrearDepartamentoRequest;
import com.workflow.departamento.dto.DepartamentoResponse;
import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartamentoService {
    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public DepartamentoResponse crearDepartamento(String empresaId, CrearDepartamentoRequest request) {
        Departamento depto = new Departamento();
        depto.setEmpresaId(empresaId);
        depto.setNombre(request.getNombre());
        depto.setDescripcion(request.getDescripcion());

        String adminId = normalizarId(request.getAdminDepartamentoId());
        validarAdminDepartamento(empresaId, adminId);
        depto.setAdminDepartamentoId(adminId);
        
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

        String adminId = normalizarId(request.getAdminDepartamentoId());
        validarAdminDepartamento(empresaId, adminId);
        depto.setAdminDepartamentoId(adminId);

        Departamento updated = departamentoRepository.save(depto);
        return DepartamentoResponse.fromEntity(updated);
    }

    public void eliminarDepartamento(String empresaId, String deptoId) {
        Departamento depto = departamentoRepository.findByIdAndEmpresaId(deptoId, empresaId)
            .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
        if (usuarioRepository.countByDepartamentoIdAndActivoTrue(deptoId) > 0) {
            throw new RuntimeException("No se puede eliminar, tiene usuarios asignados");
        }
        depto.setActivo(false);
        depto.setAdminDepartamentoId(null);
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
        return departamentoRepository.findByEmpresaIdAndActivo(empresaId, true).stream()
            .filter(d -> d.getAdminDepartamentoId() == null || d.getAdminDepartamentoId().isBlank())
            .map(DepartamentoResponse::fromEntity)
            .collect(Collectors.toList());
    }

    public List<DepartamentoResponse> listarDepartamentosCompletos(String empresaId) {
        return departamentoRepository.findByEmpresaIdAndActivo(empresaId, true).stream()
            .filter(d -> d.getAdminDepartamentoId() != null && !d.getAdminDepartamentoId().isBlank())
            .map(DepartamentoResponse::fromEntity)
            .collect(Collectors.toList());
    }

    private String normalizarId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void validarAdminDepartamento(String empresaId, String adminId) {
        if (adminId == null) {
            return;
        }

        Usuario admin = usuarioRepository.findByIdAndActivoTrue(adminId)
            .orElseThrow(() -> new RuntimeException("Admin de departamento no encontrado"));

        if (!empresaId.equals(admin.getEmpresaId())) {
            throw new RuntimeException("El admin de departamento debe pertenecer a la misma empresa");
        }

        if (!"ADMIN_DEPARTAMENTO".equals(admin.getRol())) {
            throw new RuntimeException("El usuario asignado debe tener rol ADMIN_DEPARTAMENTO");
        }
    }
}
