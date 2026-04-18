package com.workflow.tramite.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tramites")
public class Tramite {
    @Id
    private String id;

    @Field("politica_id")
    private String politicaId;

    @Field("empresa_id")
    private String empresaId;

    private String titulo;

    @Field("estado_general")
    private String estadoGeneral; // PENDIENTE, EN_PROCESO, COMPLETADO, RECHAZADO

    @Field("nodo_actual_id")
    private String nodoActualId;

    private String prioridad; // ALTA, MEDIA, BAJA

    @Field("fecha_limite")
    private LocalDateTime fechaLimite;

    @Field("iniciado_por")
    private String iniciadoPor; // Usuario ID

    @CreatedDate
    @Field("iniciado_en")
    private LocalDateTime iniciadoEn;

    @Field("finalizado_en")
    private LocalDateTime finalizadoEn;

    @Field("nodos_paralelos_pendientes")
    private List<String> nodosParalelosPendientes;

    @Field("iteraciones_por_nodo")
    private Map<String, Integer> iteracionesPorNodo;
}
