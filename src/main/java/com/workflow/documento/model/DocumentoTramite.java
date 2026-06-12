package com.workflow.documento.model;

import com.workflow.formulario.model.LlenadoPor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "documentos_tramite")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoTramite {

    @Id
    private String id;

    private String empresaId;
    private String clienteId;
    private String tramiteId;
    private String politicaId;
    private String nodoId;
    private String departamentoNombre;   // nombre del depto donde se subió
    private String campoClave;           // nombre del campo del formulario al que pertenece
    private String nombreArchivo;
    private String tipoMime;
    private String urlS3;                // URL pública
    private String s3Key;                // key para pre-firmar o eliminar
    private Long tamanioBytes;
    private String subidoPor;            // userId — puede ser el cliente o el funcionario
    private LlenadoPor subidoPorRol;     // CLIENTE | FUNCIONARIO
    private LocalDateTime subidoEn;

    private Integer version = 1;
    private List<VersionDocumento> historialVersiones = new ArrayList<>();
}
