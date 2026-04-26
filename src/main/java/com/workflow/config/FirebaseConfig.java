package com.workflow.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials:}")
    private String firebaseCredentials;

    @PostConstruct
    public void initialize() {
        if (firebaseCredentials == null || firebaseCredentials.isBlank()) {
            log.warn("Firebase no configurado (firebase.credentials vacio). Push notifications deshabilitadas.");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            InputStream credentialsStream;
            if (firebaseCredentials.startsWith("{")) {
                credentialsStream = new ByteArrayInputStream(firebaseCredentials.getBytes(StandardCharsets.UTF_8));
            } else if (firebaseCredentials.startsWith("classpath:")) {
                String classpathLocation = firebaseCredentials.substring("classpath:".length());
                credentialsStream = FirebaseConfig.class.getResourceAsStream(
                        classpathLocation.startsWith("/") ? classpathLocation : "/" + classpathLocation
                );
                if (credentialsStream == null) {
                    log.warn("No se encontro archivo classpath de Firebase en '{}'. Push notifications deshabilitadas.", classpathLocation);
                    return;
                }
            } else if (firebaseCredentials.endsWith(".json")) {
                Path credentialsPath = Paths.get(firebaseCredentials).toAbsolutePath().normalize();
                if (!Files.exists(credentialsPath)) {
                    log.warn("No se encontro archivo de credenciales Firebase en '{}'. Push notifications deshabilitadas.", credentialsPath);
                    return;
                }
                credentialsStream = new FileInputStream(credentialsPath.toFile());
            } else {
                byte[] decoded = Base64.getDecoder().decode(firebaseCredentials);
                credentialsStream = new ByteArrayInputStream(decoded);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase inicializado correctamente.");
        } catch (Exception e) {
            log.warn("Error al inicializar Firebase: {}. Push notifications deshabilitadas.", e.getMessage());
        }
    }
}
