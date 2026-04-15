package com.workflow.transicion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transiciones")
public class Transicion {
    @Id
    private String id;

    private String politicaId;
    private String nodoOrigenId;
    private String nodoDestinoId;
    private String tipo; // LINEAL | ALTERNATIVA | PARALELA
    private String condicion;
    private String etiqueta;
    private Boolean activo;

    @CreatedDate
    private LocalDateTime creadoEn;
}
