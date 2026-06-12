package com.workflow.documento.controller;

import com.workflow.documento.dto.OnlyOfficeCallbackDTO;
import com.workflow.documento.service.DocumentoService;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/onlyoffice")
@RequiredArgsConstructor
@Slf4j
public class OnlyOfficeCallbackController {

    private final DocumentoService documentoService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/callback/{documentoId}")
    public ResponseEntity<Map<String, Integer>> callback(
            @PathVariable String documentoId,
            @RequestBody OnlyOfficeCallbackDTO body) {
        try {
            if (body.getStatus() == 2 && body.getUrl() != null) {
                log.info("OnlyOffice callback: guardando nueva version de documento {}", documentoId);
                byte[] contenido = descargarDesdeUrl(body.getUrl());
                String actor = resolverNombreEditores(body.getUsers());
                documentoService.guardarVersionDesdeBytes(documentoId, contenido, actor);
                log.info("Version guardada para documento {} por: {}", documentoId, actor);
            }
        } catch (Exception e) {
            log.error("Error procesando callback OnlyOffice para documento {}: {}", documentoId, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("error", 0));
    }

    /** Resuelve los IDs de usuarios que editaron el documento a sus nombres reales. */
    private String resolverNombreEditores(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return "OnlyOffice";
        }
        List<String> nombres = userIds.stream()
            .map(uid -> usuarioRepository.findById(uid)
                .map(u -> u.getNombre() != null && !u.getNombre().isBlank() ? u.getNombre() : u.getEmail())
                .orElse(uid))
            .collect(Collectors.toList());
        return String.join(", ", nombres);
    }

    private byte[] descargarDesdeUrl(String urlString) throws Exception {
        try (InputStream stream = new URL(urlString).openStream()) {
            return stream.readAllBytes();
        }
    }
}
