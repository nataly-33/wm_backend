package com.workflow.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String id;
    private String nombre;
    private String email;
    private String rol;
    private String empresaId;
    private String departamentoId; // null si es Admin General
}
