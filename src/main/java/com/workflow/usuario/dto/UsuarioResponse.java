package com.workflow.usuario.dto;

import com.workflow.usuario.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private String id;
    private String nombre;
    private String email;
    private String rol;
    private String departamentoId;
    private Boolean activo;
    private LocalDateTime creadoEn;

    public static UsuarioResponse fromEntity(Usuario usuario) {
        return new UsuarioResponse(
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail(),
            usuario.getRol(),
            usuario.getDepartamentoId(),
            usuario.getActivo(),
            usuario.getCreadoEn()
        );
    }
}
