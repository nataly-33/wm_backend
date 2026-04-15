package com.workflow.politica.dto;

import com.workflow.politica.model.Politica;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private Integer version;
    private String estado;
    private Boolean generadaPorIa;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    public static PoliticaResponse fromEntity(Politica politica) {
        return new PoliticaResponse(
                politica.getId(),
                politica.getNombre(),
                politica.getDescripcion(),
                politica.getVersion(),
                politica.getEstado(),
                politica.getGeneradaPorIa(),
                politica.getCreadoPor(),
                politica.getCreadoEn(),
                politica.getActualizadoEn()
        );
    }
}
