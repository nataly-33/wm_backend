package com.workflow.formulario.dto;

import com.workflow.formulario.model.Formulario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormularioResponse {
    private String id;
    private String politicaId;
    private String nodoId;
    private String nombre;
    private List<FormularioCampoResponse> campos;
    private Boolean generadoPorIa;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    public static FormularioResponse fromEntity(Formulario formulario) {
        List<FormularioCampoResponse> campos = formulario.getCampos() == null
                ? List.of()
                : formulario.getCampos().stream().map(FormularioCampoResponse::fromEntity).toList();
        return new FormularioResponse(
                formulario.getId(),
                formulario.getPoliticaId(),
                formulario.getNodoId(),
                formulario.getNombre(),
                campos,
                formulario.getGeneradoPorIa(),
                formulario.getCreadoPor(),
                formulario.getCreadoEn(),
                formulario.getActualizadoEn()
        );
    }
}
