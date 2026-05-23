package com.workflow.documento.controller;

import com.workflow.documento.dto.OnlyOfficeCallbackDTO;
import com.workflow.documento.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/onlyoffice")
@RequiredArgsConstructor
@Slf4j
public class OnlyOfficeCallbackController {

    private final DocumentoService documentoService;

    @PostMapping("/callback/{documentoId}")
    public ResponseEntity<Map<String, Integer>> callback(
            @PathVariable String documentoId,
            @RequestBody OnlyOfficeCallbackDTO body) {
        try {
            if (body.getStatus() == 2 && body.getUrl() != null) {
                log.info("OnlyOffice callback: guardando nueva version de documento {}", documentoId);
                byte[] contenido = descargarDesdeUrl(body.getUrl());
                documentoService.guardarVersionDesdeBytes(documentoId, contenido, "OnlyOffice");
                log.info("Version guardada para documento {}", documentoId);
            }
        } catch (Exception e) {
            log.error("Error procesando callback OnlyOffice para documento {}: {}", documentoId, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("error", 0));
    }

    private byte[] descargarDesdeUrl(String urlString) throws Exception {
        try (InputStream stream = new URL(urlString).openStream()) {
            return stream.readAllBytes();
        }
    }
}
