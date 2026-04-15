package com.workflow.politica.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearPoliticaRequest {
    private String nombre;
    private String descripcion;
}
