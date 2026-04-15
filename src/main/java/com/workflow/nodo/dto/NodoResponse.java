package com.workflow.nodo.dto;

import com.workflow.nodo.model.Nodo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodoResponse {
    private String id;
    private String politicaId;
    private String departamentoId;
    private String nombre;
    private String tipo;
    private Double posicionX;
    private Double posicionY;
    private String formularioId;
    private LocalDateTime creadoEn;

    public static NodoResponse fromEntity(Nodo nodo) {
        return new NodoResponse(
                nodo.getId(),
                nodo.getPoliticaId(),
                nodo.getDepartamentoId(),
                nodo.getNombre(),
                nodo.getTipo(),
                nodo.getPosicionX(),
                nodo.getPosicionY(),
                nodo.getFormularioId(),
                nodo.getCreadoEn()
        );
    }
}
