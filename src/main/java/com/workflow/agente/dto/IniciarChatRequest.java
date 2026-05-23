package com.workflow.agente.dto;

import lombok.Data;

@Data
public class IniciarChatRequest {
    private String clienteId;
    private String mensajeInicial;
}
