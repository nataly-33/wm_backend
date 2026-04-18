package com.workflow.politica.dto;

import com.workflow.departamento.dto.DepartamentoResponse;
import com.workflow.nodo.dto.NodoResponse;
import com.workflow.transicion.dto.TransicionResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramaResponse {
    private String datosDiagramaJson;
    private List<NodoResponse> nodos;
    private List<TransicionResponse> transiciones;
    private List<DepartamentoResponse> departamentos;
}
