package com.workflow.documento.dto;

import com.workflow.documento.model.PermisosDocumento;
import com.workflow.documento.model.VersionDocumento;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DocumentoResponse {
    private String id;
    private String empresaId;
    private String nombre;
    private String descripcion;
    private String tipoMime;
    private String urlArchivo;
    private String s3Key;
    private Long tamanioBytes;
    private String carpetaId;
    private String politicaId;
    private String tramiteId;
    private List<String> etiquetas;
    private Integer version;
    private List<VersionDocumento> historialVersiones;
    private PermisosDocumento permisos;
    private String creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime creadoEn;
    private LocalDateTime modificadoEn;
}
