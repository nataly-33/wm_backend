package com.workflow.ejecucion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampoRellenadoDto {
    private String nombre;
    private String etiqueta;
    private String tipo;
    private Object valor;
    private boolean esArchivo;
}
