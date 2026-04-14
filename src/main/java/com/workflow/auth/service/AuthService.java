package com.workflow.auth.service;

import com.workflow.auth.dto.AuthRequest;
import com.workflow.auth.dto.AuthResponse;
import com.workflow.auth.dto.RegistroRequest;
import com.workflow.empresa.model.Empresa;
import com.workflow.empresa.repository.EmpresaRepository;
import com.workflow.security.JwtUtil;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registro de nueva empresa y admin general
     */
    public AuthResponse registro(RegistroRequest request) {
        // Verificar que el email no exista
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Crear la empresa
        Empresa empresa = Empresa.builder()
                .nombre(request.getNombreEmpresa())
                .activo(true)
                .build();
        empresa = empresaRepository.save(empresa);

        // Crear el Admin General
        Usuario usuario = Usuario.builder()
                .empresaId(empresa.getId())
                .nombre(request.getNombreAdmin())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol("ADMIN_GENERAL")
                .departamentoId(null)
                .activo(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        // Generar JWT
        String token = jwtUtil.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol());

        return AuthResponse.builder()
                .token(token)
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .departamentoId(usuario.getDepartamentoId())
                .build();
    }

    /**
     * Login del usuario
     */
    public AuthResponse login(AuthRequest request) {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        // Generar JWT
        String token = jwtUtil.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol());

        return AuthResponse.builder()
                .token(token)
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .departamentoId(usuario.getDepartamentoId())
                .build();
    }
}
