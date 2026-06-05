package com.workflow.reporte.service;

import com.workflow.reporte.dto.EspecificacionReporte;
import com.workflow.tramite.repository.TramiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteService {

    private final TramiteRepository tramiteRepository;
    private final RestTemplate restTemplate;

    @Value("${ia.service.url:http://localhost:8001}")
    private String iaServiceUrl;

    public EspecificacionReporte interpretarConsulta(String consulta) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> payload = Map.of("consulta", consulta);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);
        try {
            return restTemplate.postForObject(iaServiceUrl + "/ia/generar-reporte", entity, EspecificacionReporte.class);
        } catch (Exception e) {
            log.error("Error interpretando consulta de reporte: {}", e.getMessage());
            EspecificacionReporte fallback = new EspecificacionReporte();
            fallback.setTipoReporte("TRAMITES_POR_ESTADO");
            fallback.setFormatoExportacion("PANTALLA");
            fallback.setPreguntasFaltantes(new ArrayList<>());
            fallback.setDescripcionReporte("Reporte de tramites");
            return fallback;
        }
    }

    public List<Map<String, Object>> ejecutarConsulta(EspecificacionReporte spec, String empresaId) {
        List<Map<String, Object>> resultados = new ArrayList<>();
        try {
            String tipo = spec.getTipoReporte() != null ? spec.getTipoReporte() : "";
            switch (tipo) {
                case "TRAMITES_POR_ESTADO" -> {
                    tramiteRepository.findByEmpresaId(empresaId).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                            t -> t.getEstadoGeneral() != null ? t.getEstadoGeneral() : "DESCONOCIDO",
                            java.util.stream.Collectors.counting()
                        ))
                        .forEach((estado, count) -> {
                            Map<String, Object> row = new HashMap<>();
                            row.put("estado", estado);
                            row.put("cantidad", count);
                            resultados.add(row);
                        });
                }
                default -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("tipo_reporte", tipo);
                    row.put("empresaId", empresaId);
                    row.put("mensaje", "Consulta procesada por IA");
                    resultados.add(row);
                }
            }
        } catch (Exception e) {
            log.error("Error ejecutando consulta de reporte: {}", e.getMessage());
        }
        return resultados;
    }
}
