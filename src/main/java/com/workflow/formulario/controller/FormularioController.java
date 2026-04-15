package com.workflow.formulario.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.formulario.dto.CrearFormularioRequest;
import com.workflow.formulario.dto.FormularioResponse;
import com.workflow.formulario.service.FormularioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/formularios")
@RequiredArgsConstructor
public class FormularioController {
    private final FormularioService formularioService;

    @GetMapping("/nodo/{nodoId}")
    public ResponseEntity<ApiResponse<FormularioResponse>> obtenerPorNodo(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String nodoId) {
        return ResponseEntity.ok(ApiResponse.success("Formulario obtenido", formularioService.obtenerPorNodo(empresaId, nodoId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FormularioResponse>> crear(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("rol") String rol,
            @RequestBody CrearFormularioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Formulario creado", formularioService.crear(empresaId, userId, rol, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FormularioResponse>> actualizar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("rol") String rol,
            @PathVariable String id,
            @RequestBody CrearFormularioRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Formulario actualizado", formularioService.actualizar(empresaId, userId, rol, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("rol") String rol,
            @PathVariable String id) {
        formularioService.eliminar(empresaId, userId, rol, id);
        return ResponseEntity.ok(ApiResponse.success("Formulario eliminado", null));
    }
}
