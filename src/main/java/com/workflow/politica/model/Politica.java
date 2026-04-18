package com.workflow.politica.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "politicas")
public class Politica {
    @Id
    private String id;

    private String empresaId;
    private String nombre;
    private String descripcion;
    private Integer version;
    private String estado; // BORRADOR | ACTIVA | INACTIVA
    private Boolean generadaPorIa;
    private String creadoPor;
    private Boolean activo;
    private String datosDiagramaJson;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;
}
