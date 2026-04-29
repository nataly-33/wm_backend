package com.workflow.ia.controller;

import com.workflow.common.dto.ApiResponse;
import com.workflow.ia.dto.GenerarDiagramaRequest;
import com.workflow.ia.dto.GenerarFormularioRequest;
import com.workflow.ia.service.IaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "IA")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/ia")
@RequiredArgsConstructor
@Slf4j
public class IaController {

    private final IaService iaService;

    @Operation(summary = "Generar diagrama UML desde texto")
    @PostMapping("/generar-diagrama")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<Object>> generarDiagrama(
            @RequestBody GenerarDiagramaRequest request) {
        log.info("Generando diagrama: prompt='{}', departamentos={}",
                request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(80, request.getPrompt().length())) : "",
                request.getDepartamentos() != null ? request.getDepartamentos().size() : 0);
        return ResponseEntity.ok(iaService.generarDiagrama(request));
    }

    @Operation(summary = "Analizar politica en busca de cuellos de botella")
    @PostMapping("/analizar-politica")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ApiResponse<Object>> analizarPolitica(
            @RequestBody Map<String, String> body) {
        String politicaId = body.get("politicaId");
        if (politicaId == null || politicaId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("politicaId es requerido"));
        }
        log.info("Iniciando analisis de politica: {}", politicaId);
        ApiResponse<Object> respuesta = iaService.analizarPolitica(politicaId);
        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Generar campos de formulario desde descripción")
    @PostMapping("/generar-formulario")
    @PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ADMIN_DEPARTAMENTO')")
    public ResponseEntity<ApiResponse<Object>> generarFormulario(
            @RequestBody GenerarFormularioRequest request) {
        return ResponseEntity.ok(iaService.generarFormulario(request));
    }

    @Operation(summary = "Sugerir valor para un campo de formulario con IA")
    @PostMapping("/sugerir-campo")
    @PreAuthorize("hasRole('FUNCIONARIO') or hasRole('ADMIN_GENERAL') or hasRole('ADMIN_DEPARTAMENTO')")
    public ResponseEntity<ApiResponse<Object>> sugerirCampo(
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(iaService.sugerirCampo(request));
    }
}
