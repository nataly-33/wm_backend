package com.workflow.departamento.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.departamento.dto.CrearDepartamentoRequest;
import com.workflow.departamento.dto.DepartamentoResponse;
import com.workflow.departamento.service.DepartamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departamentos")
@RequiredArgsConstructor
public class DepartamentoController {
    private final DepartamentoService departamentoService;

    @PostMapping
    public ResponseEntity<ApiResponse<DepartamentoResponse>> crearDepartamento(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @RequestBody CrearDepartamentoRequest request) {
        DepartamentoResponse response = departamentoService.crearDepartamento(empresaId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Departamento creado", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartamentoResponse>>> listarDepartamentos(
            @RequestHeader("X-Empresa-Id") String empresaId) {
        List<DepartamentoResponse> response = departamentoService.listarDepartamentos(empresaId);
        return ResponseEntity.ok(ApiResponse.success("Departamentos obtenidos", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartamentoResponse>> obtenerDepartamento(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        DepartamentoResponse response = departamentoService.obtenerDepartamento(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Departamento obtenido", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartamentoResponse>> actualizarDepartamento(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody CrearDepartamentoRequest request) {
        DepartamentoResponse response = departamentoService.actualizarDepartamento(empresaId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Departamento actualizado", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarDepartamento(
            @RequestHeader("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        departamentoService.eliminarDepartamento(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Departamento eliminado", null));
    }
}
