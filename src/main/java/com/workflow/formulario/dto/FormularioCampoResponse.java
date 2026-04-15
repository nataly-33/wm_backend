package com.workflow.formulario.dto;

import com.workflow.formulario.model.Formulario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormularioCampoResponse {
    private String nombre;
    private String etiqueta;
    private String tipo;
    private Boolean requerido;
    private Boolean esCampoPrioridad;
    private List<String> opciones;

    public static FormularioCampoResponse fromEntity(Formulario.CampoFormulario campo) {
        return new FormularioCampoResponse(
                campo.getNombre(),
                campo.getEtiqueta(),
                campo.getTipo(),
                campo.getRequerido(),
                campo.getEsCampoPrioridad(),
                campo.getOpciones()
        );
    }
}
