package com.workflow.reporte.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EspecificacionReporte {
    @JsonProperty("tipo_reporte")
    private String tipoReporte;
    private Map<String, Object> filtros;
    private String agrupacion;
    private String ordenamiento;
    @JsonProperty("ordenar_por")
    private String ordenarPor;
    private int limite;
    @JsonProperty("formato_exportacion")
    private String formatoExportacion;
    @JsonProperty("preguntas_faltantes")
    private List<String> preguntasFaltantes;
    @JsonProperty("descripcion_reporte")
    private String descripcionReporte;
    @JsonProperty("confianza_nlp")
    private double confianzaNlp;
}
