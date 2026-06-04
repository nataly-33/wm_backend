package com.workflow.archivo.controller;

import com.workflow.archivo.service.ArchivoService;
import com.workflow.documento.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final ArchivoService archivoService;

    @Autowired(required = false)
    private S3Service s3Service;

    @PostMapping("/subir")
    public ResponseEntity<Map<String, String>> subir(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) String empresaId,
            @RequestParam(required = false) String clienteId,
            @RequestParam(required = false) String tramiteId,
            @RequestParam(required = false) String departamento) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío"));
        }

        try {
            String url = archivoService.subirArchivo(archivo, empresaId, clienteId, tramiteId, departamento);
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

    /**
     * Genera URL pre-firmada para un archivo en S3 (válida 60 minutos).
     * Resuelve el problema de visualización/descarga cuando el bucket S3 es privado.
     *
     * Para archivos de formulario: GET /api/v1/archivos/presignado?key=empresaId/clienteId/...
     * Para URL pública almacenada: GET /api/v1/archivos/presignado?url=https://bucket.s3.amazonaws.com/...
     */
    @GetMapping("/presignado")
    public ResponseEntity<Map<String, String>> presignado(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String url,
            @RequestParam(defaultValue = "60") int minutos) {

        if (s3Service == null) {
            String fallback = url != null ? url : (key != null ? "/api/v1/archivos/" + key : "");
            return ResponseEntity.ok(Map.of("url", fallback));
        }

        String resolvedKey;
        if (key != null && !key.isBlank()) {
            resolvedKey = key;
        } else if (url != null && !url.isBlank()) {
            resolvedKey = s3Service.extraerKeyDeUrl(url);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Se requiere 'key' o 'url'"));
        }

        String firmada = s3Service.generarUrlPresignada(resolvedKey, minutos);
        return ResponseEntity.ok(Map.of("url", firmada, "key", resolvedKey));
    }

    /**
     * Proxy de visualización/descarga: redirige con HTTP 302 a la URL pre-firmada de S3.
     * Soluciona la visualización de imágenes y descarga de archivos cuando el bucket es privado.
     *
     * Acepta la URL pública original almacenada en MongoDB como parámetro:
     *   GET /api/v1/archivos/ver?url=https://bucket.s3.amazonaws.com/empresaId/...
     *
     * Si el archivo es local (comienza con /api/v1/archivos/), redirige directamente.
     * Si S3 no está configurado, redirige a la misma URL (comportamiento sin cambios).
     */
    @GetMapping("/ver")
    public ResponseEntity<Void> verArchivo(@RequestParam String url) {
        // Archivo local: redirigir al endpoint de descarga directo
        if (url.startsWith("/api/v1/archivos/") || !url.startsWith("http")) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
        }

        // Sin S3: redirigir a la URL tal cual (puede ser simulada o pública)
        if (s3Service == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
        }

        // S3 activo: generar URL pre-firmada y redirigir
        String key = s3Service.extraerKeyDeUrl(url);
        String urlFirmada = s3Service.generarUrlPresignada(key, 60);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(urlFirmada))
            .build();
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
