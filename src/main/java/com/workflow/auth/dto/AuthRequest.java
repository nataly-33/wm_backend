package com.workflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    
    @NotBlank(message = "El email es requerido")
    private String email;
    
    @NotBlank(message = "La contraseña es requerida")
    private String password;
}
