package com.workflow.agente.dto;

import lombok.Data;

@Data
public class SubirDocumentoAgenteRequest {
    private String conversacionId;
    private String clienteId;
    private String archivoUrl;
    private String nombreArchivo;
}
