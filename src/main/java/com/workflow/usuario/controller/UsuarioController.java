package com.workflow.usuario.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.usuario.dto.CrearUsuarioRequest;
import com.workflow.usuario.dto.UsuarioResponse;
import com.workflow.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<ApiResponse<UsuarioResponse>> crearUsuario(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = usuarioService.crearUsuario(empresaId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Usuario creado", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> listarUsuarios(
            @RequestHeader("X-Empresa-Id") String empresaId) {
        List<UsuarioResponse> response = usuarioService.listarUsuarios(empresaId);
        return ResponseEntity.ok(ApiResponse.success("Usuarios obtenidos", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponse>> obtenerUsuario(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        UsuarioResponse response = usuarioService.obtenerUsuario(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Usuario obtenido", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponse>> actualizarUsuario(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = usuarioService.actualizarUsuario(empresaId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Usuario actualizado", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        usuarioService.eliminarUsuario(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Usuario eliminado", null));
    }
}
