package com.workflow.documento.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "documentos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Documento {
    @Id private String id;
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
    @Builder.Default
    private List<VersionDocumento> historialVersiones = new ArrayList<>();
    private PermisosDocumento permisos;
    private String creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime creadoEn;
    private LocalDateTime modificadoEn;
    @Builder.Default
    private boolean eliminado = false;
}
