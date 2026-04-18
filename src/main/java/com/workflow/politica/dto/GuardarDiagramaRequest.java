package com.workflow.politica.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuardarDiagramaRequest {
    private String datosDiagramaJson;
    private List<NodoDiagramaPayload> nodos;
    private List<TransicionDiagramaPayload> transiciones;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodoDiagramaPayload {
        private String id;
        private String tempId;
        private String tipo;
        private String nombre;
        private String departamentoId;
        private String formularioId;
        private Double posicionX;
        private Double posicionY;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransicionDiagramaPayload {
        private String id;
        private String nodoOrigenTempId;
        private String nodoDestinoTempId;
        private String tipo;
        private String etiqueta;
        private String condicion;
    }
}
