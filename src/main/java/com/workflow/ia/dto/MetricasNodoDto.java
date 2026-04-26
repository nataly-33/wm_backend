package com.workflow.ia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricasNodoDto {
    private String nodo_id;
    private String nombre_nodo;
    private double tiempo_promedio_minutos;
    private int cantidad_ejecuciones_activas;
    private double tasa_rechazo;
    private double tiempo_espera_promedio_minutos;
    private double varianza_tiempo;
}
