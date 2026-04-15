package com.workflow.transicion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearTransicionRequest {
    private String politicaId;
    private String nodoOrigenId;
    private String nodoDestinoId;
    private String tipo;
    private String condicion;
    private String etiqueta;
}
