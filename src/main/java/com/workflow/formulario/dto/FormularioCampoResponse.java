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
    private Integer filas;
    private List<String> columnas;

    public static FormularioCampoResponse fromEntity(Formulario.CampoFormulario campo) {
        FormularioCampoResponse r = new FormularioCampoResponse();
        r.setNombre(campo.getNombre());
        r.setEtiqueta(campo.getEtiqueta());
        r.setTipo(campo.getTipo());
        r.setRequerido(campo.getRequerido());
        r.setEsCampoPrioridad(campo.getEsCampoPrioridad());
        r.setOpciones(campo.getOpciones());
        r.setFilas(campo.getFilas());
        r.setColumnas(campo.getColumnas());
        return r;
    }
}
