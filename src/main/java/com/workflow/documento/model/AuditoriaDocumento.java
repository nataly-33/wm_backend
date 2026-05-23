package com.workflow.documento.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "auditoria_documentos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaDocumento {
    @Id private String id;
    private String documentoId;
    private String usuarioId;
    private String usuarioNombre;
    private String accion;
    private String detalles;
    private LocalDateTime fechaHora;
}
