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
public class TramiteDetalladoResponse {
    private String id;
    private String titulo;
    private String estadoGeneral;
    private String prioridad;
    private String nombrePolitica;
    private String nodoActualNombre;
    private String departamentoActualNombre;
    private String funcionarioActualNombre;
    private LocalDateTime iniciadoEn;
    private LocalDateTime finalizadoEn;
    private LocalDateTime fechaLimite;
    private String tiempoTranscurrido;
    private String duracionTotal;
    private String motivoRechazo;
    private String nodoRechazoNombre;
    private String departamentoRechazoNombre;
}
