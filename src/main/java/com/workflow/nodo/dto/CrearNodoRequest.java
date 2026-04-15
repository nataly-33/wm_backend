package com.workflow.nodo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearNodoRequest {
    private String politicaId;
    private String departamentoId;
    private String nombre;
    private String tipo;
    private Double posicionX;
    private Double posicionY;
    private String formularioId;
}
