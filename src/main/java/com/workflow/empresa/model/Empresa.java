package com.workflow.empresa.model;

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
@Document(collection = "empresas")
public class Empresa {

    @Id
    private String id;

    private String nombre;

    private String logoUrl;

    private Boolean activo;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;
}
