package com.workflow.agente.dto;

import lombok.Data;

@Data
public class MensajeChatRequest {
    private String conversacionId;
    private String clienteId;
    private String mensaje;
    private String tipo; // "texto" | "confirmacion" | "rechazo"
}
