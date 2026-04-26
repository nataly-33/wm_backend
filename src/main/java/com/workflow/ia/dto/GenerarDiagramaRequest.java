package com.workflow.ia.dto;

import lombok.Data;

import java.util.List;

@Data
public class GenerarDiagramaRequest {
    private String prompt;
    private List<DepartamentoIaDto> departamentos;
    private String politicaId;

    @Data
    public static class DepartamentoIaDto {
        private String id;
        private String nombre;
    }
}
