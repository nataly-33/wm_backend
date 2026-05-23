package com.workflow.agente.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensajeChat {
    private String rol;       // "agente" | "cliente"
    private String contenido;
    private String tipo;      // "texto" | "archivo" | "confirmacion" | "estado"
    private LocalDateTime timestamp;
}
