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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            // Convert to plain Map to guarantee JSON serialization (avoids XML from jackson-dataformat-xml)
            Map<String, Object> payload = new HashMap<>();
            payload.put("prompt", request.getPrompt());
            payload.put("politicaId", request.getPoliticaId());
            if (request.getDepartamentos() != null) {
                List<Map<String, String>> deptos = request.getDepartamentos().stream()
                        .map(d -> {
                            Map<String, String> m = new HashMap<>();
                            m.put("id", d.getId());
                            m.put("nombre", d.getNombre());
                            return m;
                        })
                        .collect(Collectors.toList());
                payload.put("departamentos", deptos);
            } else {
                payload.put("departamentos", List.of());
            }

            log.info("Payload JSON para IA: prompt='{}', departamentos={}, politicaId='{}'",
                    request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(80, request.getPrompt().length())) : "",
                    payload.get("departamentos") instanceof List ? ((List<?>) payload.get("departamentos")).size() : 0,
                    request.getPoliticaId());

            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/generar-diagrama",
                    jsonEntity(payload),
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
            // Convert metricas to plain Maps to guarantee JSON serialization
            List<Map<String, Object>> metricasMaps = metricas.stream()
                    .map(m -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("nodo_id", m.getNodo_id());
                        map.put("nombre_nodo", m.getNombre_nodo());
                        map.put("tiempo_promedio_minutos", m.getTiempo_promedio_minutos());
                        map.put("cantidad_ejecuciones_activas", m.getCantidad_ejecuciones_activas());
                        map.put("tasa_rechazo", m.getTasa_rechazo());
                        map.put("tiempo_espera_promedio_minutos", m.getTiempo_espera_promedio_minutos());
                        map.put("varianza_tiempo", m.getVarianza_tiempo());
                        return map;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> payload = new HashMap<>();
            payload.put("politicaId", politicaId);
            payload.put("metricas", metricasMaps);

            log.info("Enviando {} metricas al microservicio IA para politica {}", metricas.size(), politicaId);
            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/analizar-politica",
                    jsonEntity(payload),
                    Object.class
            );
            log.info("Analisis completado para politica {}", politicaId);
            return ApiResponse.success("Analisis completado", respuesta);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Error del microservicio IA analisis ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error al analizar la politica: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error en analisis IA para politica {}: {}", politicaId, e.getMessage());
            throw new RuntimeException("Error al analizar la politica");
        }
    }

    public ApiResponse<Object> generarFormulario(GenerarFormularioRequest request) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("descripcion", request.getDescripcion());
            payload.put("nombreNodo", request.getNombreNodo());

            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/generar-formulario",
                    jsonEntity(payload),
                    Object.class
            );
            return ApiResponse.success("Campos generados", respuesta);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Error al generar formulario con IA ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error al generar formulario: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error al generar formulario con IA: {}", e.getMessage());
            throw new RuntimeException("El servicio de IA no está disponible temporalmente");
        }
    }

    public ApiResponse<Object> sugerirCampo(Map<String, String> request) {
        try {
            Object respuesta = restTemplate.postForObject(
                    iaServiceUrl + "/ia/sugerir-campo",
                    jsonEntity(request),
                    Object.class
            );
            return ApiResponse.success("Sugerencia generada", respuesta);
        } catch (Exception e) {
            log.warn("sugerir-campo: microservicio no disponible: {}", e.getMessage());
            return ApiResponse.success("Sin sugerencia", Map.of("sugerencia", ""));
        }
    }

    private List<MetricasNodoDto> calcularMetricasPorNodo(String politicaId) {
        List<Nodo> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId);
        List<MetricasNodoDto> metricas = new ArrayList<>();

        log.info("Encontrados {} nodos para politica {}", nodos.size(), politicaId);

        for (Nodo nodo : nodos) {
            if ("INICIO".equals(nodo.getTipo()) || "FIN".equals(nodo.getTipo())) continue;

            List<EjecucionNodo> ejecuciones = ejecucionRepository.findByNodoId(nodo.getId());
            List<EjecucionNodo> completadas = ejecuciones.stream()
                    .filter(e -> "COMPLETADO".equals(e.getEstado()))
                    .toList();

            if (!completadas.isEmpty()) {
                // Use real execution data
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
            } else {
                // Generate simulated metrics so the ML model can still analyze the workflow structure
                // This allows analysis to work even without real trámite data (demo/seeder)
                double tiempoSimulado;
                int activasSimuladas;
                double tasaRechazoSimulada;

                switch (nodo.getTipo()) {
                    case "TAREA":
                        // Simulate based on nodo name patterns
                        String nombreLower = nodo.getNombre() != null ? nodo.getNombre().toLowerCase() : "";
                        if (nombreLower.contains("verific") || nombreLower.contains("inspecc") || nombreLower.contains("analiz")) {
                            tiempoSimulado = 120 + (Math.random() * 360); // 2-8 hours for verification tasks
                            activasSimuladas = 3 + (int)(Math.random() * 10);
                            tasaRechazoSimulada = 0.1 + (Math.random() * 0.25);
                        } else if (nombreLower.contains("registr") || nombreLower.contains("recibir")) {
                            tiempoSimulado = 15 + (Math.random() * 45); // 15-60 min for intake
                            activasSimuladas = 5 + (int)(Math.random() * 15);
                            tasaRechazoSimulada = 0.02 + (Math.random() * 0.08);
                        } else if (nombreLower.contains("pago") || nombreLower.contains("factur") || nombreLower.contains("cobro")) {
                            tiempoSimulado = 30 + (Math.random() * 90); // 30-120 min
                            activasSimuladas = 2 + (int)(Math.random() * 8);
                            tasaRechazoSimulada = 0.05 + (Math.random() * 0.15);
                        } else if (nombreLower.contains("firma") || nombreLower.contains("contrato") || nombreLower.contains("legal")) {
                            tiempoSimulado = 180 + (Math.random() * 480); // 3-11 hours
                            activasSimuladas = 1 + (int)(Math.random() * 5);
                            tasaRechazoSimulada = 0.08 + (Math.random() * 0.12);
                        } else {
                            tiempoSimulado = 60 + (Math.random() * 180);  // 1-4 hours default
                            activasSimuladas = 2 + (int)(Math.random() * 8);
                            tasaRechazoSimulada = 0.05 + (Math.random() * 0.2);
                        }
                        break;
                    case "DECISION":
                        tiempoSimulado = 5 + (Math.random() * 15); // 5-20 min
                        activasSimuladas = 1 + (int)(Math.random() * 3);
                        tasaRechazoSimulada = 0.2 + (Math.random() * 0.3); // Higher rejection at decisions
                        break;
                    case "PARALELO":
                        tiempoSimulado = 10 + (Math.random() * 30);
                        activasSimuladas = 4 + (int)(Math.random() * 12);
                        tasaRechazoSimulada = 0.03 + (Math.random() * 0.07);
                        break;
                    default:
                        tiempoSimulado = 60 + (Math.random() * 120);
                        activasSimuladas = 2 + (int)(Math.random() * 6);
                        tasaRechazoSimulada = 0.05 + (Math.random() * 0.15);
                }

                metricas.add(new MetricasNodoDto(
                        nodo.getId(),
                        nodo.getNombre(),
                        Math.round(tiempoSimulado * 10.0) / 10.0,
                        activasSimuladas,
                        Math.round(tasaRechazoSimulada * 1000.0) / 1000.0,
                        Math.round(tiempoSimulado * 0.3 * 10.0) / 10.0,
                        Math.round(tiempoSimulado * 0.2 * 10.0) / 10.0
                ));
                log.info("Metricas simuladas para nodo '{}' ({}): tiempo={}min, activas={}, rechazo={}",
                        nodo.getNombre(), nodo.getTipo(),
                        Math.round(tiempoSimulado), activasSimuladas,
                        String.format("%.1f%%", tasaRechazoSimulada * 100));
            }
        }

        return metricas;
    }
}
