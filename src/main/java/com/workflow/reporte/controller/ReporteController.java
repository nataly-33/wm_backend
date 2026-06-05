package com.workflow.reporte.controller;

import com.workflow.reporte.dto.EspecificacionReporte;
import com.workflow.reporte.dto.ReporteConsultaRequest;
import com.workflow.reporte.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @PostMapping("/interpretar")
    public ResponseEntity<EspecificacionReporte> interpretarConsulta(
            @RequestBody ReporteConsultaRequest request) {
        return ResponseEntity.ok(reporteService.interpretarConsulta(request.getConsulta()));
    }

    @PostMapping("/generar")
    public ResponseEntity<?> generarReporte(@RequestBody ReporteConsultaRequest request) {
        EspecificacionReporte spec = reporteService.interpretarConsulta(request.getConsulta());

        if (spec.getPreguntasFaltantes() != null && !spec.getPreguntasFaltantes().isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "estado", "PREGUNTAS_PENDIENTES",
                "preguntas", spec.getPreguntasFaltantes(),
                "descripcion", spec.getDescripcionReporte() != null ? spec.getDescripcionReporte() : ""
            ));
        }

        List<Map<String, Object>> datos = reporteService.ejecutarConsulta(spec, request.getEmpresaId());
        return ResponseEntity.ok(Map.of(
            "datos", datos,
            "descripcion", spec.getDescripcionReporte() != null ? spec.getDescripcionReporte() : "",
            "tipo_reporte", spec.getTipoReporte() != null ? spec.getTipoReporte() : "",
            "formato", spec.getFormatoExportacion() != null ? spec.getFormatoExportacion() : "PANTALLA"
        ));
    }
}
