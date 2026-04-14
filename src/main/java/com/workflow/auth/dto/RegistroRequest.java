package com.workflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRequest {
    
    @NotBlank(message = "El nombre de la empresa es requerido")
    private String nombreEmpresa;
    
    @NotBlank(message = "El nombre del admin es requerido")
    private String nombreAdmin;
    
    @NotBlank(message = "El email es requerido")
    private String email;
    
    @NotBlank(message = "La contraseña es requerida")
    private String password;
}
