package com.workflow.usuario.service;

import com.workflow.usuario.dto.CrearUsuarioRequest;
import com.workflow.usuario.dto.UsuarioResponse;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
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
        if ((request.getRol().equals("FUNCIONARIO") || request.getRol().equals("ADMIN_DEPARTAMENTO"))
                && (request.getDepartamentoId() == null || request.getDepartamentoId().isEmpty())) {
            throw new RuntimeException("Departamento requerido para ADMIN_DEPARTAMENTO y FUNCIONARIO");
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
        return UsuarioResponse.fromEntity(saved);
    }

    public UsuarioResponse actualizarUsuario(String empresaId, String usuarioId, CrearUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.getEmpresaId().equals(empresaId)) {
            throw new RuntimeException("No tienes permiso para actualizar este usuario");
        }

        usuario.setNombre(request.getNombre());
        usuario.setRol(request.getRol());
        usuario.setDepartamentoId(request.getDepartamentoId());
        usuario.setActualizadoEn(LocalDateTime.now());

        Usuario updated = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(updated);
    }

    public void eliminarUsuario(String empresaId, String usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.getEmpresaId().equals(empresaId)) {
            throw new RuntimeException("No tienes permiso para eliminar este usuario");
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
