package com.workflow.usuario.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    private String empresaId;

    private String nombre;

    private String email;

    private String passwordHash;

    private String rol; // ADMIN_GENERAL, ADMIN_DEPARTAMENTO, FUNCIONARIO

    private String departamentoId; // null si es Admin General

    private String fcmToken; // Token de Firebase para notificaciones push

    private Boolean activo;

    @CreatedDate
    private LocalDateTime creadoEn;

    @LastModifiedDate
    private LocalDateTime actualizadoEn;
}
