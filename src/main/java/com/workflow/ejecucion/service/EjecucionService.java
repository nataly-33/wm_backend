package com.workflow.ejecucion.service;

import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.tramite.service.MotorWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EjecucionService {
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final MotorWorkflowService motorWorkflowService;

    public List<EjecucionNodo> listarPorDepartamento(String departamentoId) {
        // Devuelve las pendientes/en proceso para un depto
        return ejecucionNodoRepository.findByDepartamentoIdAndEstado(departamentoId, "PENDIENTE");
    }

    public void completar(String id, Map<String, Object> respuesta) {
        motorWorkflowService.completarEjecucion(id, respuesta);
    }

    public void rechazar(String id, String observaciones) {
        motorWorkflowService.rechazarEjecucion(id, observaciones);
    }

    public EjecucionNodo obtener(String id) {
        return ejecucionNodoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Ejecución no encontrada"));
    }
}
