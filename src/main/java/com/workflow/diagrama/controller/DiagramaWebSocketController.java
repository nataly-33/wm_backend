package com.workflow.diagrama.controller;

import com.workflow.diagrama.dto.DiagramaEventoDTO;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class DiagramaWebSocketController {

    @MessageMapping("/diagrama/{politicaId}/evento")
    @SendTo("/topic/diagrama/{politicaId}")
    public DiagramaEventoDTO procesarEvento(
            @DestinationVariable String politicaId,
            DiagramaEventoDTO evento) {
        return evento;
    }
}
