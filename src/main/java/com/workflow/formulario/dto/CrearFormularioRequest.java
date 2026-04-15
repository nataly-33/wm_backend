package com.workflow.formulario.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearFormularioRequest {
    private String politicaId;
    private String nodoId;
    private String nombre;
    private List<FormularioCampoRequest> campos;
    private Boolean generadoPorIa;
}
