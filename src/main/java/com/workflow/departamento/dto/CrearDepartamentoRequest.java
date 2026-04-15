package com.workflow.departamento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearDepartamentoRequest {
    private String nombre;
    private String descripcion;
    private String adminDepartamentoId;
}
