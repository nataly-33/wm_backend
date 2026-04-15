package com.workflow.nodo.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.nodo.dto.ActualizarPosicionNodoRequest;
import com.workflow.nodo.dto.CrearNodoRequest;
import com.workflow.nodo.dto.NodoResponse;
import com.workflow.nodo.service.NodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/nodos")
@RequiredArgsConstructor
public class NodoController {
    private final NodoService nodoService;

    @GetMapping("/politica/{politicaId}")
    public ResponseEntity<ApiResponse<List<NodoResponse>>> listarPorPolitica(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String politicaId) {
        return ResponseEntity.ok(ApiResponse.success("Nodos obtenidos", nodoService.listarPorPolitica(empresaId, politicaId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NodoResponse>> crear(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestBody CrearNodoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nodo creado", nodoService.crear(empresaId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NodoResponse>> actualizar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody CrearNodoRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Nodo actualizado", nodoService.actualizar(empresaId, id, request)));
    }

    @PutMapping("/{id}/posicion")
    public ResponseEntity<ApiResponse<NodoResponse>> actualizarPosicion(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody ActualizarPosicionNodoRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Posicion actualizada", nodoService.actualizarPosicion(empresaId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        nodoService.eliminar(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Nodo eliminado", null));
    }
}
