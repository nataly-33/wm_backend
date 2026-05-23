package com.workflow.agente.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "conversaciones_agente")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversacionAgente {
    @Id
    private String id;
    private String clienteId;
    private String tramiteId;
    private String politicaId;
    private String nodoActualId;
    private EstadoConversacion estado;
    private List<MensajeChat> mensajes;
    private Map<String, Object> datosRecopilados;
    private List<String> archivosSubidos;
    private LocalDateTime creadoEn;
    private LocalDateTime ultimaActividadEn;
}
