package com.workflow.documento.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermisosDocumento {
    private List<String> puedenVer;
    private List<String> puedenEditar;
    private List<String> puedenEliminar;
}
