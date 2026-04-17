package com.workflow.notificacion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notificaciones")
public class Notificacion {
    @Id
    private String id;

    @Field("usuario_id")
    private String usuarioId;

    @Field("tramite_id")
    private String tramiteId;

    @Field("nodo_id")
    private String nodoId;

    private String tipo; // ASIGNACION, COMPLETADO, RECHAZADO, PRIORIDAD, ALERTA_IA

    private String mensaje;

    private Boolean leida;

    @CreatedDate
    @Field("creado_en")
    private LocalDateTime creadoEn;
}
