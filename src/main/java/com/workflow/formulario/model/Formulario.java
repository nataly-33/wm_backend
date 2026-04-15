package com.workflow.formulario.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "formularios")
public class Formulario {
    @Id
    private String id;

    private String politicaId;
    private String nodoId;
    private String nombre;
    private List<CampoFormulario> campos;
    private Boolean generadoPorIa;
    private String creadoPor;
    private Boolean activo;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampoFormulario {
        private String nombre;
        private String etiqueta;
        private String tipo;
        private Boolean requerido;
        private Boolean esCampoPrioridad;
        private List<String> opciones;
    }
}
