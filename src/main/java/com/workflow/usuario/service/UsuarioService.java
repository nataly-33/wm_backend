package com.workflow.usuario.service;

import com.workflow.usuario.dto.CrearUsuarioRequest;
import com.workflow.usuario.dto.UsuarioResponse;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.departamento.model.Departamento;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UsuarioResponse crearUsuario(String empresaId, CrearUsuarioRequest request) {
        // Validar credenciales
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Validar rol
        if (!request.getRol().equals("ADMIN_DEPARTAMENTO") && !request.getRol().equals("FUNCIONARIO")) {
            throw new RuntimeException("Rol inválido. Debe ser ADMIN_DEPARTAMENTO o FUNCIONARIO");
        }

        // Validar departamento para roles de departamento
        if (request.getRol().equals("FUNCIONARIO") || request.getRol().equals("ADMIN_DEPARTAMENTO")) {
            if (request.getDepartamentoId() == null || request.getDepartamentoId().trim().isEmpty()) {
                throw new RuntimeException("Departamento requerido para ADMIN_DEPARTAMENTO y FUNCIONARIO");
            }
            Departamento depto = departamentoRepository.findByIdAndEmpresaId(request.getDepartamentoId(), empresaId)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado en esta empresa"));
            
            if (request.getRol().equals("ADMIN_DEPARTAMENTO")) {
                if (depto.getAdminDepartamentoId() != null && !depto.getAdminDepartamentoId().trim().isEmpty()) {
                    throw new RuntimeException("Este departamento ya tiene un Admin asignado");
                }
            }
        } else if (request.getRol().equals("ADMIN_GENERAL")) {
            request.setDepartamentoId(null);
        }

        Usuario usuario = Usuario.builder()
            .empresaId(empresaId)
            .nombre(request.getNombre())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .rol(request.getRol())
            .departamentoId(request.getDepartamentoId())
            .activo(true)
            .creadoEn(LocalDateTime.now())
            .build();

        Usuario saved = usuarioRepository.save(usuario);
        
        if (saved.getRol().equals("ADMIN_DEPARTAMENTO")) {
            Departamento depto = departamentoRepository.findById(saved.getDepartamentoId()).orElseThrow();
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

        String rolAnterior = usuario.getRol();
        String deptoAnterior = usuario.getDepartamentoId();

        if (request.getRol().equals("FUNCIONARIO") || request.getRol().equals("ADMIN_DEPARTAMENTO")) {
            if (request.getDepartamentoId() == null || request.getDepartamentoId().trim().isEmpty()) {
                throw new RuntimeException("Departamento requerido para ADMIN_DEPARTAMENTO y FUNCIONARIO");
            }
        } else if (request.getRol().equals("ADMIN_GENERAL")) {
            request.setDepartamentoId(null);
        }

        // Lógica para actualizar/limpiar Admin del Departamento anterior
        if ("ADMIN_DEPARTAMENTO".equals(rolAnterior)) {
            if (!request.getRol().equals("ADMIN_DEPARTAMENTO") || 
                (request.getDepartamentoId() != null && !request.getDepartamentoId().equals(deptoAnterior))) {
                if (deptoAnterior != null) {
                    departamentoRepository.findById(deptoAnterior).ifPresent(d -> {
                        d.setAdminDepartamentoId(null);
                        departamentoRepository.save(d);
                    });
                }
            }
        }

        // Validar nuevo departamento
        if (request.getRol().equals("ADMIN_DEPARTAMENTO")) {
            Departamento nuevoDepto = departamentoRepository.findByIdAndEmpresaId(request.getDepartamentoId(), empresaId)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado"));
            if (nuevoDepto.getAdminDepartamentoId() != null 
                && !nuevoDepto.getAdminDepartamentoId().equals(usuario.getId()) 
                && !nuevoDepto.getAdminDepartamentoId().trim().isEmpty()) {
                throw new RuntimeException("Este departamento ya tiene un Admin asignado");
            }
        }

        usuario.setNombre(request.getNombre());
        usuario.setRol(request.getRol());
        usuario.setDepartamentoId(request.getDepartamentoId());
        usuario.setActualizadoEn(LocalDateTime.now());

        Usuario updated = usuarioRepository.save(usuario);

        if (updated.getRol().equals("ADMIN_DEPARTAMENTO")) {
            Departamento nuevoDepto = departamentoRepository.findById(updated.getDepartamentoId()).orElseThrow();
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

        if ("ADMIN_DEPARTAMENTO".equals(usuario.getRol()) && usuario.getDepartamentoId() != null) {
            departamentoRepository.findById(usuario.getDepartamentoId()).ifPresent(d -> {
                d.setAdminDepartamentoId(null);
                departamentoRepository.save(d);
            });
        }
        
        usuario.setActivo(false);
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
}
