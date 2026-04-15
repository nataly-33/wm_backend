package com.workflow.nodo.model;

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
@Document(collection = "nodos")
public class Nodo {
    @Id
    private String id;

    private String politicaId;
    private String departamentoId;
    private String nombre;
    private String tipo; // INICIO | TAREA | DECISION | FIN | PARALELO
    private Double posicionX;
    private Double posicionY;
    private String formularioId;
    private Boolean activo;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;
}
