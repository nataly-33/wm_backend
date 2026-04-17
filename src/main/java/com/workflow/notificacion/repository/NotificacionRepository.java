package com.workflow.notificacion.repository;

import com.workflow.notificacion.model.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(String usuarioId);
    List<Notificacion> findByUsuarioIdAndLeidaFalse(String usuarioId);
}
