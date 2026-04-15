package com.workflow.usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearUsuarioRequest {
    private String nombre;
    private String email;
    private String password;
    private String rol; // ADMIN_DEPARTAMENTO, FUNCIONARIO
    private String departamentoId; // null para ADMIN_DEPARTAMENTO, requerido para FUNCIONARIO
}
