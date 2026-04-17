package com.workflow.notificacion.service;

import com.workflow.notificacion.model.Notificacion;
import com.workflow.notificacion.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Notificacion crearNotificacion(String usuarioId, String tramiteId, String nodoId, String tipo, String mensaje) {
        Notificacion notificacion = Notificacion.builder()
            .usuarioId(usuarioId)
            .tramiteId(tramiteId)
            .nodoId(nodoId)
            .tipo(tipo)
            .mensaje(mensaje)
            .leida(false)
            .creadoEn(LocalDateTime.now())
            .build();

        Notificacion saved = notificacionRepository.save(notificacion);

        // Emitir notificacion por WebSocket al canal del usuario
        if (usuarioId != null) {
            messagingTemplate.convertAndSend("/topic/usuario/" + usuarioId, saved);
        }

        return saved;
    }

    public List<Notificacion> obtenerNoLeidas(String usuarioId) {
        return notificacionRepository.findByUsuarioIdAndLeidaFalse(usuarioId);
    }

    public void marcarComoLeida(String notificacionId) {
        notificacionRepository.findById(notificacionId).ifPresent(notif -> {
            notif.setLeida(true);
            notificacionRepository.save(notif);
        });
    }

    // Emitir eventos de monitor a toda la politica
    public void notificarCambioMonitor(String politicaId, Object payload) {
        messagingTemplate.convertAndSend("/topic/politica/" + politicaId, payload);
    }
}
