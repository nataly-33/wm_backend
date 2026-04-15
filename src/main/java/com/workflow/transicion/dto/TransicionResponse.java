package com.workflow.transicion.dto;

import com.workflow.transicion.model.Transicion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransicionResponse {
    private String id;
    private String politicaId;
    private String nodoOrigenId;
    private String nodoDestinoId;
    private String tipo;
    private String condicion;
    private String etiqueta;
    private LocalDateTime creadoEn;

    public static TransicionResponse fromEntity(Transicion transicion) {
        return new TransicionResponse(
                transicion.getId(),
                transicion.getPoliticaId(),
                transicion.getNodoOrigenId(),
                transicion.getNodoDestinoId(),
                transicion.getTipo(),
                transicion.getCondicion(),
                transicion.getEtiqueta(),
                transicion.getCreadoEn()
        );
    }
}
