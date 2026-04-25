package com.workflow.ejecucion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EjecucionDetalladaResponse {
    private String id;
    private String estado;
    private String nombreNodo;
    private String nombrePolitica;
    private String tituloTramite;
    private String prioridad;
    private LocalDateTime fechaLimite;
    private LocalDateTime iniciadoEn;
    private String nombreDepartamento;
    private String tramiteId;
    private String politicaId;
    private String nodoId;
}
