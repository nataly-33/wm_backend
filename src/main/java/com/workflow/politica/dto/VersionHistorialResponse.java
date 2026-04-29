package com.workflow.politica.dto;

import com.workflow.politica.model.VersionHistorial;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionHistorialResponse {
    private String id;
    private Integer version;
    private String hash;
    private String cambiadoPor;
    private String nombreCambiadoPor;
    private LocalDateTime fechaCambio;
    private String tipoAccion;
    private String descripcion;
    private String nombrePolitica;
    private String estadoResultante;
    private Integer nNodos;
    private Integer nTransiciones;

    public static VersionHistorialResponse fromEntity(VersionHistorial v) {
        return new VersionHistorialResponse(
                v.getId(),
                v.getVersion(),
                v.getHash(),
                v.getCambiadoPor(),
                v.getNombreCambiadoPor(),
                v.getFechaCambio(),
                v.getTipoAccion(),
                v.getDescripcion(),
                v.getNombrePolitica(),
                v.getEstadoResultante(),
                v.getNNodos(),
                v.getNTransiciones()
        );
    }
}
