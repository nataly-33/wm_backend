package com.workflow.usuario.service;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.usuario.dto.CrearUsuarioRequest;
import com.workflow.usuario.dto.UsuarioResponse;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private static final Set<String> ROLES_VALIDOS = Set.of("ADMIN_GENERAL", "ADMIN_DEPARTAMENTO", "FUNCIONARIO");

    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UsuarioResponse crearUsuario(String empresaId, CrearUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El email ya esta registrado");
        }

        validarRol(request.getRol());
        String departamentoId = normalizarDepartamentoId(request.getDepartamentoId());

        if ("ADMIN_GENERAL".equals(request.getRol())) {
            if (departamentoId != null) {
                throw new RuntimeException("ADMIN_GENERAL no debe tener departamento asignado");
            }
        }

        Departamento depto = null;
        if (requiereDepartamento(request.getRol())) {
            if (departamentoId == null) {
                throw new RuntimeException("Departamento requerido para ADMIN_DEPARTAMENTO y FUNCIONARIO");
            }
            depto = obtenerDepartamentoEmpresa(departamentoId, empresaId);
            if ("ADMIN_DEPARTAMENTO".equals(request.getRol()) && tieneAdminAsignado(depto)) {
                throw new RuntimeException("Este departamento ya tiene un Admin asignado");
            }
        }

        Usuario usuario = Usuario.builder()
                .empresaId(empresaId)
                .nombre(request.getNombre())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(request.getRol())
                .departamentoId(departamentoId)
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build();

        Usuario saved = usuarioRepository.save(usuario);

        if ("ADMIN_DEPARTAMENTO".equals(saved.getRol()) && depto != null) {
            depto.setAdminDepartamentoId(saved.getId());
            departamentoRepository.save(depto);
        }

        return UsuarioResponse.fromEntity(saved);
    }

    public UsuarioResponse actualizarUsuario(String empresaId, String usuarioId, CrearUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.getEmpresaId().equals(empresaId)) {
            throw new RuntimeException("No tienes permiso para actualizar este usuario");
        }

        validarRol(request.getRol());

        String rolAnterior = usuario.getRol();
        String deptoAnteriorId = normalizarDepartamentoId(usuario.getDepartamentoId());

        String nuevoRol = request.getRol();
        String nuevoDeptoId = normalizarDepartamentoId(request.getDepartamentoId());

        if ("ADMIN_GENERAL".equals(nuevoRol) && nuevoDeptoId != null) {
            throw new RuntimeException("ADMIN_GENERAL no debe tener departamento asignado");
        }

        Departamento nuevoDepto = null;
        if (requiereDepartamento(nuevoRol)) {
            if (nuevoDeptoId == null) {
                throw new RuntimeException("Departamento requerido para ADMIN_DEPARTAMENTO y FUNCIONARIO");
            }
            nuevoDepto = obtenerDepartamentoEmpresa(nuevoDeptoId, empresaId);
        }

        if ("ADMIN_DEPARTAMENTO".equals(rolAnterior)
                && (!"ADMIN_DEPARTAMENTO".equals(nuevoRol) || !stringEqualsSafe(deptoAnteriorId, nuevoDeptoId))) {
            limpiarAdminSiCoincide(deptoAnteriorId, usuario.getId(), empresaId);
        }

        if ("ADMIN_DEPARTAMENTO".equals(nuevoRol) && nuevoDepto != null) {
            String adminActual = normalizarDepartamentoId(nuevoDepto.getAdminDepartamentoId());
            if (adminActual != null && !adminActual.equals(usuario.getId())) {
                throw new RuntimeException("Este departamento ya tiene un Admin asignado");
            }
        }

        usuario.setNombre(request.getNombre());
        usuario.setRol(nuevoRol);
        usuario.setDepartamentoId(nuevoDeptoId);
        usuario.setActualizadoEn(LocalDateTime.now());

        Usuario updated = usuarioRepository.save(usuario);

        if ("ADMIN_DEPARTAMENTO".equals(nuevoRol) && nuevoDepto != null) {
            nuevoDepto.setAdminDepartamentoId(updated.getId());
            departamentoRepository.save(nuevoDepto);
        }

        return UsuarioResponse.fromEntity(updated);
    }

    public void eliminarUsuario(String empresaId, String usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.getEmpresaId().equals(empresaId)) {
            throw new RuntimeException("No tienes permiso para eliminar este usuario");
        }

        if ("ADMIN_DEPARTAMENTO".equals(usuario.getRol())) {
            limpiarAdminSiCoincide(usuario.getDepartamentoId(), usuario.getId(), empresaId);
        }

        usuario.setActivo(false);
        usuario.setActualizadoEn(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    public UsuarioResponse obtenerUsuario(String empresaId, String usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.getEmpresaId().equals(empresaId)) {
            throw new RuntimeException("No tienes permiso para ver este usuario");
        }

        return UsuarioResponse.fromEntity(usuario);
    }

    public List<UsuarioResponse> listarUsuarios(String empresaId) {
        return usuarioRepository.findByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(UsuarioResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private void validarRol(String rol) {
        if (rol == null || !ROLES_VALIDOS.contains(rol)) {
            throw new RuntimeException("Rol invalido. Debe ser ADMIN_GENERAL, ADMIN_DEPARTAMENTO o FUNCIONARIO");
        }
    }

    private boolean requiereDepartamento(String rol) {
        return "ADMIN_DEPARTAMENTO".equals(rol) || "FUNCIONARIO".equals(rol);
    }

    private String normalizarDepartamentoId(String departamentoId) {
        if (departamentoId == null || departamentoId.trim().isEmpty()) {
            return null;
        }
        return departamentoId.trim();
    }

    private Departamento obtenerDepartamentoEmpresa(String departamentoId, String empresaId) {
        return departamentoRepository.findByIdAndEmpresaId(departamentoId, empresaId)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado en esta empresa"));
    }

    private boolean tieneAdminAsignado(Departamento departamento) {
        return departamento.getAdminDepartamentoId() != null && !departamento.getAdminDepartamentoId().isBlank();
    }

    private void limpiarAdminSiCoincide(String departamentoId, String usuarioId, String empresaId) {
        String deptoId = normalizarDepartamentoId(departamentoId);
        if (deptoId == null) {
            return;
        }

        departamentoRepository.findByIdAndEmpresaId(deptoId, empresaId).ifPresent(d -> {
            String admin = normalizarDepartamentoId(d.getAdminDepartamentoId());
            if (admin != null && admin.equals(usuarioId)) {
                d.setAdminDepartamentoId(null);
                departamentoRepository.save(d);
            }
        });
    }

    private boolean stringEqualsSafe(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
