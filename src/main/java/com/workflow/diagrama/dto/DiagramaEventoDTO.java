package com.workflow.diagrama.dto;

import lombok.Data;
import java.util.Map;

@Data
public class DiagramaEventoDTO {
    private String tipo;
    private String elementoId;
    private String usuarioId;
    private Map<String, Object> datos;
}
