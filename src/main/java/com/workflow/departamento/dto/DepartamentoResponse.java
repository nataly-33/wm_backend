package com.workflow.departamento.dto;

import com.workflow.departamento.model.Departamento;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartamentoResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private String adminDepartamentoId;
    private Boolean activo;
    private LocalDateTime creadoEn;

    public static DepartamentoResponse fromEntity(Departamento depto) {
        return new DepartamentoResponse(
            depto.getId(),
            depto.getNombre(),
            depto.getDescripcion(),
            depto.getAdminDepartamentoId(),
            depto.getActivo(),
            depto.getCreadoEn()
        );
    }
}
