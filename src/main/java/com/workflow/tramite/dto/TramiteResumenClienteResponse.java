package com.workflow.tramite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TramiteResumenClienteResponse {
    private String id;
    private String nombre;
    private String politicaNombre;
    private String asignadoA;
    private String estado;
    private String prioridad;
    private LocalDateTime iniciadoEn;
    private int tiempoTranscurridoMinutos;
    private String nodoActualNombre;
}
