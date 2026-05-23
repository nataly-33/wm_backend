package com.workflow.documento.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDocumento {
    private Integer version;
    private String urlArchivo;
    private String s3Key;
    private LocalDateTime fechaSubida;
    private String subidoPorId;
    private String subidoPorNombre;
    private Long tamanioBytes;
}
