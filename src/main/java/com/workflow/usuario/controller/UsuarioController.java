package com.workflow.usuario.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.usuario.dto.CrearUsuarioRequest;
import com.workflow.usuario.dto.UsuarioResponse;
import com.workflow.usuario.repository.UsuarioRepository;
import com.workflow.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<UsuarioResponse>> crearUsuario(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = usuarioService.crearUsuario(empresaId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Usuario creado", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> listarUsuarios(
            @RequestAttribute("X-Empresa-Id") String empresaId) {
        List<UsuarioResponse> response = usuarioService.listarUsuarios(empresaId);
        return ResponseEntity.ok(ApiResponse.success("Usuarios obtenidos", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponse>> obtenerUsuario(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        UsuarioResponse response = usuarioService.obtenerUsuario(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Usuario obtenido", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponse>> actualizarUsuario(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = usuarioService.actualizarUsuario(empresaId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Usuario actualizado", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        usuarioService.eliminarUsuario(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Usuario eliminado", null));
    }

    @PutMapping("/{id}/fcm-token")
    public ResponseEntity<?> actualizarFcmToken(
            @PathVariable String id,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestBody Map<String, String> body) {
        String fcmToken = body.get("fcmToken");
        if (fcmToken == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "fcmToken requerido"));
        }
        return usuarioRepository.findById(id).map(usuario -> {
            usuario.setFcmToken(fcmToken.isBlank() ? null : fcmToken);
            usuarioRepository.save(usuario);
            if (fcmToken.isBlank()) {
                log.info("[FCM] Token eliminado para usuario {} ({})", usuario.getEmail(), id);
            } else {
                log.info("[FCM] Token registrado para usuario {} ({})", usuario.getEmail(), id);
            }
            return ResponseEntity.ok(Map.of("message", "FCM token actualizado"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
