package com.workflow.politica.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.politica.dto.CrearPoliticaRequest;
import com.workflow.politica.dto.PoliticaResponse;
import com.workflow.politica.service.PoliticaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/politicas")
@RequiredArgsConstructor
public class PoliticaController {
    private final PoliticaService politicaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PoliticaResponse>>> listar(
            @RequestAttribute("X-Empresa-Id") String empresaId) {
        return ResponseEntity.ok(ApiResponse.success("Politicas obtenidas", politicaService.listarPorEmpresa(empresaId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PoliticaResponse>> obtener(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Politica obtenida", politicaService.obtener(empresaId, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PoliticaResponse>> crear(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @RequestAttribute("userId") String userId,
            @RequestBody CrearPoliticaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Politica creada", politicaService.crear(empresaId, userId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PoliticaResponse>> actualizar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id,
            @RequestBody CrearPoliticaRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Politica actualizada", politicaService.actualizar(empresaId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        politicaService.eliminar(empresaId, id);
        return ResponseEntity.ok(ApiResponse.success("Politica eliminada", null));
    }

    @PutMapping("/{id}/activar")
    public ResponseEntity<ApiResponse<PoliticaResponse>> activar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Politica activada", politicaService.activar(empresaId, id)));
    }

    @PutMapping("/{id}/desactivar")
    public ResponseEntity<ApiResponse<PoliticaResponse>> desactivar(
            @RequestAttribute("X-Empresa-Id") String empresaId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Politica desactivada", politicaService.desactivar(empresaId, id)));
    }
}
