package com.workflow.nodo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarPosicionNodoRequest {
    private Double posicionX;
    private Double posicionY;
}
