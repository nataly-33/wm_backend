package com.workflow.ejecucion.service;

import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.tramite.service.MotorWorkflowService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EjecucionService {
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final MotorWorkflowService motorWorkflowService;

    public List<EjecucionNodo> listarPorDepartamento(String departamentoId) {
        return ejecucionNodoRepository.findByDepartamentoIdAndEstadoIn(
                departamentoId,
                List.of("PENDIENTE", "EN_PROCESO", "PENDIENTE_SIN_ASIGNAR")
        );
    }

    public List<EjecucionNodo> listarPorFuncionario(String funcionarioId) {
        return ejecucionNodoRepository.findByFuncionarioIdAndEstadoIn(
                funcionarioId,
                List.of("PENDIENTE", "EN_PROCESO")
        );
    }

    public List<EjecucionNodo> listarPorTramite(String tramiteId) {
        return ejecucionNodoRepository.findByTramiteIdOrderByIniciadoEnDesc(tramiteId);
    }

    public EjecucionNodo iniciar(String id, String userId) {
        EjecucionNodo ejecucion = obtener(id);
        if (!"PENDIENTE".equals(ejecucion.getEstado())) {
            if ("EN_PROCESO".equals(ejecucion.getEstado())) {
                return ejecucion;
            }
            throw new RuntimeException("Solo se pueden iniciar ejecuciones en estado PENDIENTE");
        }

        if (ejecucion.getFuncionarioId() != null && userId != null && !ejecucion.getFuncionarioId().equals(userId)) {
            throw new RuntimeException("Esta tarea está asignada a otro funcionario");
        }

        if (ejecucion.getFuncionarioId() == null && userId != null && !userId.isBlank()) {
            ejecucion.setFuncionarioId(userId);
        }

        ejecucion.setEstado("EN_PROCESO");
        if (ejecucion.getIniciadoEn() == null) {
            ejecucion.setIniciadoEn(LocalDateTime.now());
        }
        return ejecucionNodoRepository.save(ejecucion);
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

    public EjecucionNodo reasignar(String id, String funcionarioId) {
        EjecucionNodo ejecucion = obtener(id);
        if ("COMPLETADO".equals(ejecucion.getEstado()) || "RECHAZADO".equals(ejecucion.getEstado())) {
            throw new RuntimeException("No se puede reasignar una ejecución ya finalizada");
        }

        ejecucion.setFuncionarioId(funcionarioId);
        if ("PENDIENTE_SIN_ASIGNAR".equals(ejecucion.getEstado())) {
            ejecucion.setEstado("PENDIENTE");
        }
        return ejecucionNodoRepository.save(ejecucion);
    }
}
