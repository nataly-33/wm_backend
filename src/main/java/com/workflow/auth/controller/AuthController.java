package com.workflow.auth.controller;

import com.workflow.auth.dto.AuthRequest;
import com.workflow.auth.dto.AuthResponse;
import com.workflow.auth.dto.RegistroRequest;
import com.workflow.auth.service.AuthService;
import com.workflow.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Registro de nueva empresa y admin general
     */
    @Operation(summary = "Registrar nueva empresa con admin general")
    @PostMapping("/registro")
    public ResponseEntity<ApiResponse<AuthResponse>> registro(@Valid @RequestBody RegistroRequest request) {
        log.info("Registro de nueva empresa: {}", request.getNombreEmpresa());
        AuthResponse response = authService.registro(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Empresa registrada exitosamente", response));
    }

    /**
     * Login del usuario
     */
    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login del usuario: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login exitoso", response));
    }

    /**
     * Verificar que el servidor está activo
     */
    @Operation(summary = "Health check")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Servidor activo", "OK"));
    }
}
