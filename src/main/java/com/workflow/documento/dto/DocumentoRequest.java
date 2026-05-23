package com.workflow.documento.dto;

import com.workflow.documento.model.PermisosDocumento;
import lombok.Data;
import java.util.List;

@Data
public class DocumentoRequest {
    private String empresaId;
    private String nombre;
    private String descripcion;
    private String carpetaId;
    private String politicaId;
    private String tramiteId;
    private List<String> etiquetas;
    private PermisosDocumento permisos;
}
