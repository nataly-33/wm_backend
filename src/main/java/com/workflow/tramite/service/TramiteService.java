package com.workflow.tramite.service;

import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TramiteService {
    private final TramiteRepository tramiteRepository;
    private final MotorWorkflowService motorWorkflowService;
    private final NodoRepository nodoRepository;

    public Tramite iniciarTramite(Map<String, Object> body, String iniciadoPor, String empresaId) {
        String politicaId = (String) body.get("politicaId");
        String titulo = (String) body.get("titulo");
        String prioridad = (String) body.get("prioridad");

        // Buscar nodo INICIO
        Nodo nodoInicio = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                .filter(n -> "INICIO".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("La política no tiene un nodo de tipo INICIO"));

        Tramite tramite = Tramite.builder()
                .politicaId(politicaId)
                .empresaId(empresaId)
                .titulo(titulo)
                .prioridad(prioridad != null ? prioridad : "MEDIA")
                .estadoGeneral("PENDIENTE")
                .iniciadoPor(iniciadoPor)
                .build();

        return motorWorkflowService.iniciarTramite(tramite, nodoInicio.getId());
    }

    public List<Tramite> listarTramitesEmpresa(String empresaId) {
        return tramiteRepository.findByEmpresaId(empresaId);
    }

    public Tramite obtenerTramite(String id) {
        return tramiteRepository.findById(id).orElseThrow(() -> new RuntimeException("Trámite no encontrado"));
    }
}
