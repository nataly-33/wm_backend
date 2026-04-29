package com.workflow.formulario.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormularioCampoRequest {
    private String nombre;
    private String etiqueta;
    private String tipo;
    private Boolean requerido;
    private Boolean esCampoPrioridad;
    private List<String> opciones;
    private Integer filas;
    private List<String> columnas;
}
