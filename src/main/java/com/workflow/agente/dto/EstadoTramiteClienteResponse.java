package com.workflow.agente.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoTramiteClienteResponse {
    private String tramiteId;
    private String titulo;
    private String estadoGeneral;
    private String nodoActualNombre;
    private String departamentoActualNombre;
    private String mensajeEstado;
    private String prioridad;
    private String iniciadoEn;
}
