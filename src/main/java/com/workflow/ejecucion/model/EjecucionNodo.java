package com.workflow.ejecucion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Document(collection = "ejecuciones_nodo")
public class EjecucionNodo {
    @Id
    private String id;

    @Field("tramite_id")
    private String tramiteId;

    @Field("nodo_id")
    private String nodoId;

    @Field("departamento_id")
    private String departamentoId;

    @Field("funcionario_id")
    private String funcionarioId; // Puede ser null inicialmente si va a una cola

    private String estado; // PENDIENTE, EN_PROCESO, COMPLETADO, RECHAZADO

    @Field("respuesta_formulario")
    private Map<String, Object> respuestaFormulario;

    @Field("archivos_adjuntos")
    private List<String> archivosAdjuntos;

    @Field("iniciado_en")
    private LocalDateTime iniciadoEn;

    @Field("completado_en")
    private LocalDateTime completadoEn;

    private String observaciones;

    // ─── Campos de las dos fases ──────────────────────────────────────────────

    private FaseNodo fase;

    @Field("respuestas_cliente")
    private Map<String, Object> respuestasCliente;

    @Field("respuestas_funcionario")
    private Map<String, Object> respuestasFuncionario;

    @Field("cliente_completado_en")
    private LocalDateTime clienteCompletadoEn;

    @Field("funcionario_completado_en")
    private LocalDateTime funcionarioCompletadoEn;

    @Field("cliente_id")
    private String clienteId;

    @Field("politica_id")
    private String politicaId;

    @Field("creado_en")
    private LocalDateTime creadoEn;
}
