package com.workflow.notificacion.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PushNotificacionService {

    private static boolean firebaseMissingLogged = false;

    private boolean isFirebaseAvailable() {
        try {
            return !FirebaseApp.getApps().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public void enviarPush(String fcmToken, String titulo, String cuerpo, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        if (!isFirebaseAvailable()) {
            if (!firebaseMissingLogged) {
                firebaseMissingLogged = true;
                log.warn("Push deshabilitado: Firebase no esta inicializado. Configure FIREBASE_CREDENTIALS en backend.");
            }
            return;
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(titulo)
                            .setBody(cuerpo)
                            .build());

            if (data != null) {
                builder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(builder.build());
            log.info("Push enviado exitosamente: {}", response);
        } catch (Exception e) {
            log.warn("Error al enviar push notification a token {}: {}", fcmToken.substring(0, Math.min(10, fcmToken.length())) + "...", e.getMessage());
        }
    }

    public void enviarPushATodos(List<String> fcmTokens, String titulo, String cuerpo, Map<String, String> data) {
        if (fcmTokens == null || fcmTokens.isEmpty()) return;
        fcmTokens.stream()
                .filter(t -> t != null && !t.isBlank())
                .forEach(token -> enviarPush(token, titulo, cuerpo, data));
    }
}
