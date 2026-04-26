package com.workflow.ia.service;

import com.workflow.common.dto.ApiResponse;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.ia.dto.GenerarDiagramaRequest;
import com.workflow.ia.dto.GenerarFormularioRequest;
import com.workflow.ia.dto.MetricasNodoDto;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IaService {

    @Value("${ia.service.url:http://localhost:8001}")
    private String iaServiceUrl;

    private final RestTemplate restTemplate;
    private final EjecucionNodoRepository ejecucionRepository;
    private final NodoRepository nodoRepository;

    private <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    public ApiResponse<Object> generarDiagrama(GenerarDiagramaRequest request) {
        log.info("Enviando a microservicio IA: prompt='{}', departamentos={}",
                request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(80, request.getPrompt().length())) : "",
                request.getDepartamentos() != null ? request.getDepartamentos().size() : 0);
        try {
            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/generar-diagrama",
                    jsonEntity(request),
                    Object.class
            );
            log.info("Microservicio IA respondio correctamente");
            return ApiResponse.success("Diagrama generado", respuesta);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Error del microservicio IA ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error al generar diagrama: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Microservicio IA no disponible: {}", e.getMessage());
            throw new RuntimeException("El servicio de IA no esta disponible temporalmente");
        }
    }

    public ApiResponse<Object> analizarPolitica(String politicaId) {
        List<MetricasNodoDto> metricas = calcularMetricasPorNodo(politicaId);

        log.info("Metricas calculadas para politica {}: {} nodos", politicaId, metricas.size());

        if (metricas.size() < 1) {
            return ApiResponse.success(
                    "Datos insuficientes para analisis. Completa al menos 2 tramites primero.",
                    Map.of(
                            "resultados", List.of(),
                            "mensaje", "Sin datos suficientes",
                            "politicaId", politicaId
                    )
            );
        }

        try {
            Map<String, Object> payload = Map.of(
                    "politicaId", politicaId,
                    "metricas", metricas
            );
            log.info("Enviando {} metricas al microservicio IA para politica {}", metricas.size(), politicaId);
            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/analizar-politica",
                    jsonEntity(payload),
                    Object.class
            );
            log.info("Analisis completado para politica {}", politicaId);
            return ApiResponse.success("Analisis completado", respuesta);
        } catch (Exception e) {
            log.error("Error en analisis IA para politica {}: {}", politicaId, e.getMessage());
            throw new RuntimeException("Error al analizar la politica");
        }
    }

    public ApiResponse<Object> generarFormulario(GenerarFormularioRequest request) {
        try {
            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/generar-formulario",
                    jsonEntity(request),
                    Object.class
            );
            return ApiResponse.success("Campos generados", respuesta);
        } catch (Exception e) {
            log.error("Error al generar formulario con IA: {}", e.getMessage());
            throw new RuntimeException("El servicio de IA no está disponible temporalmente");
        }
    }

    private List<MetricasNodoDto> calcularMetricasPorNodo(String politicaId) {
        List<Nodo> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        List<MetricasNodoDto> metricas = new ArrayList<>();

        for (Nodo nodo : nodos) {
            if ("INICIO".equals(nodo.getTipo()) || "FIN".equals(nodo.getTipo())) continue;

            List<EjecucionNodo> ejecuciones = ejecucionRepository.findByNodoId(nodo.getId());
            List<EjecucionNodo> completadas = ejecuciones.stream()
                    .filter(e -> "COMPLETADO".equals(e.getEstado()))
                    .toList();

            if (completadas.isEmpty()) continue;

            double tiempoPromedio = completadas.stream()
                    .filter(e -> e.getIniciadoEn() != null && e.getCompletadoEn() != null)
                    .mapToLong(e -> Duration.between(e.getIniciadoEn(), e.getCompletadoEn()).toMinutes())
                    .average()
                    .orElse(0);

            long activas = ejecuciones.stream()
                    .filter(e -> "PENDIENTE".equals(e.getEstado()) || "EN_PROCESO".equals(e.getEstado()))
                    .count();

            long rechazadas = ejecuciones.stream()
                    .filter(e -> "RECHAZADO".equals(e.getEstado()))
                    .count();
            double tasaRechazo = ejecuciones.isEmpty() ? 0 :
                    (double) rechazadas / ejecuciones.size();

            metricas.add(new MetricasNodoDto(
                    nodo.getId(),
                    nodo.getNombre(),
                    tiempoPromedio,
                    (int) activas,
                    tasaRechazo,
                    tiempoPromedio * 0.3,
                    tiempoPromedio * 0.2
            ));
        }

        return metricas;
    }
}
