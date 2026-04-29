package com.workflow.politica.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * Registro inmutable de cada cambio en una política.
 * Generado automáticamente al crear, editar, guardar diagrama, activar o desactivar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "version_historial")
public class VersionHistorial {

    @Id
    private String id;

    @Indexed
    private String politicaId;

    /** Número de versión en el momento del cambio */
    private Integer version;

    /**
     * Hash SHA-256 de (politicaId + version + tipoAccion + fechaCambio).
     * Sirve como huella digital única e inmutable del estado en ese momento.
     */
    private String hash;

    /** ID del usuario que realizó el cambio */
    private String cambiadoPor;

    /** Nombre visible del usuario (para mostrar sin JOIN extra) */
    private String nombreCambiadoPor;

    private LocalDateTime fechaCambio;

    /**
     * Tipo de acción que disparó este registro.
     * Valores: CREAR | DIAGRAMA | EDITAR | ACTIVAR | DESACTIVAR
     */
    private String tipoAccion;

    /** Descripción legible, ej: "Diagrama actualizado — 8 nodos, 7 transiciones" */
    private String descripcion;

    /** Snapshot mínimo del estado de la política en este momento */
    private String nombrePolitica;
    private String estadoResultante;
    private Integer nNodos;
    private Integer nTransiciones;
}
