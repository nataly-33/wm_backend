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
public class NodoHistorialResponse {
    private String ejecucionId;
    private String nodoId;
    private String nodoNombre;
    private String departamento;
    private String fase;
    private String estado;
    private List<CampoRellenadoDto> camposCliente;
    private List<CampoRellenadoDto> camposFuncionario;
    private String funcionarioNombre;
    private LocalDateTime clienteCompletadoEn;
    private LocalDateTime funcionarioCompletadoEn;
    private LocalDateTime creadoEn;
}
