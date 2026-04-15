package com.workflow.departamento.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "departamentos")
public class Departamento {
    @Id
    private String id;

    @Field("empresa_id")
    private String empresaId;

    private String nombre;
    private String descripcion;

    @Field("admin_departamento_id")
    private String adminDepartamentoId;

    private Boolean activo = true;

    @CreatedDate
    @Field("creado_en")
    private LocalDateTime creadoEn;
}
