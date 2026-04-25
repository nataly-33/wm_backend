package com.workflow.archivo.controller;

import com.workflow.archivo.service.ArchivoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final ArchivoService archivoService;

    @PostMapping("/subir")
    public ResponseEntity<Map<String, String>> subir(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío"));
        }

        try {
            String url = archivoService.subirArchivo(archivo);
            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "nombreOriginal", archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "",
                    "tipo", archivo.getContentType() != null ? archivo.getContentType() : ""
            ));
        } catch (IOException e) {
            log.error("Error al subir archivo: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el archivo"));
        }
    }

    @GetMapping("/{nombreArchivo:.+}")
    public ResponseEntity<Resource> descargar(@PathVariable String nombreArchivo) {
        try {
            Path ruta = archivoService.resolverRuta(nombreArchivo);
            Resource resource = new UrlResource(ruta.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = Files.probeContentType(ruta);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreArchivo + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
