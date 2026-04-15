package com.workflow.transicion.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.transicion.dto.CrearTransicionRequest;
import com.workflow.transicion.dto.TransicionResponse;
import com.workflow.transicion.service.TransicionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transiciones")
@RequiredArgsConstructor
public class TransicionController {
    private final TransicionService transicionService;

    @GetMapping("/politica/{politicaId}")
    public ResponseEntity<ApiResponse<List<TransicionResponse>>> listarPorPolitica(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String politicaId) {
        return ResponseEntity.ok(ApiResponse.success("Transiciones obtenidas", transicionService.listarPorPolitica(empresaId, politicaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransicionResponse>> crear(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestBody CrearTransicionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transicion creada", transicionService.crear(empresaId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TransicionResponse>> actualizar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody CrearTransicionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Transicion actualizada", transicionService.actualizar(empresaId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        transicionService.eliminar(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Transicion eliminada", null));
    }
}
