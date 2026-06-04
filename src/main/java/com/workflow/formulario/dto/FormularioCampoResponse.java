package com.workflow.formulario.dto;

import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.model.LlenadoPor;
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
    private List<String> columnasGrid;
    private LlenadoPor llenadoPor;
    private Boolean requeridoParaAvanzar;

    public static FormularioCampoResponse fromEntity(Formulario.CampoFormulario campo) {
        FormularioCampoResponse r = new FormularioCampoResponse();
        r.setNombre(campo.getNombre());
        r.setEtiqueta(campo.getEtiqueta());
        
        // Map database types back to form builder/mobile types
        String tipoNorm = campo.getTipo();
        if ("TEXTO_CORTO".equals(tipoNorm)) tipoNorm = "TEXTO";
        else if ("AREA_TEXTO".equals(tipoNorm)) tipoNorm = "TEXTAREA";
        else if ("SELECTOR".equals(tipoNorm)) tipoNorm = "SELECCION";
        else if ("TABLA_GRID".equals(tipoNorm)) tipoNorm = "GRID";
        r.setTipo(tipoNorm);
        
        r.setRequerido(campo.getRequerido());
        r.setEsCampoPrioridad(campo.getEsCampoPrioridad());
        r.setOpciones(campo.getOpciones());
        r.setFilas(campo.getFilas());
        r.setColumnas(campo.getColumnas());
        r.setColumnasGrid(campo.getColumnas());
        r.setLlenadoPor(campo.getLlenadoPor());
        r.setRequeridoParaAvanzar(campo.getRequeridoParaAvanzar());
        return r;
    }
}
