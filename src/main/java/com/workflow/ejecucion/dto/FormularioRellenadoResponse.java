package com.workflow.ejecucion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioRellenadoResponse {
    private String ejecucionId;
    private String nodoId;
    private String nombreNodo;
    private String departamentoNombre;
    private String funcionarioNombre;
    private String estado;
    private LocalDateTime completadoEn;
    private String observaciones;
    private List<CampoRellenadoDto> campos;
}
