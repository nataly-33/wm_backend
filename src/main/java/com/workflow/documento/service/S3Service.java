package com.workflow.documento.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class S3Service {

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${AWS_S3_BUCKET:wm-documentos}")
    private String bucket;

    @Value("${AWS_REGION:us-east-1}")
    private String region;

    /**
     * Sube un archivo al repositorio del cliente en S3.
     * Estructura: {empresaId}/{clienteId}/{tramiteId}/{nombreDepartamento}/{timestamp}_{nombreArchivo}
     */
    public String subirArchivo(MultipartFile archivo,
                               String empresaId,
                               String clienteId,
                               String tramiteId,
                               String nombreDepartamento) throws IOException {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Devolviendo URL simulada.");
            String key = construirKey(empresaId, clienteId, tramiteId, nombreDepartamento, archivo.getOriginalFilename());
            return "https://s3-simulado/" + key;
        }

        String key = construirKey(empresaId, clienteId, tramiteId, nombreDepartamento, archivo.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(archivo.getContentType())
            .contentDisposition("inline")
            .build();

        s3Client.putObject(request,
            RequestBody.fromInputStream(archivo.getInputStream(), archivo.getSize()));

        return obtenerUrlPublica(key);
    }

    /**
     * Sube desde bytes (usado por el callback de OnlyOffice).
     */
    public String subirDesdeBytes(byte[] contenido,
                                   String nombreArchivo,
                                   String contentType,
                                   String empresaId,
                                   String clienteId,
                                   String tramiteId,
                                   String nombreDepartamento) {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Devolviendo URL simulada.");
            String key = construirKey(empresaId, clienteId, tramiteId, nombreDepartamento, nombreArchivo);
            return "https://s3-simulado/" + key;
        }

        String key = construirKey(empresaId, clienteId, tramiteId, nombreDepartamento, nombreArchivo);

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build(),
            RequestBody.fromBytes(contenido)
        );
        return obtenerUrlPublica(key);
    }

    /** Conservado por compatibilidad con DocumentoService (callback OnlyOffice directo por key). */
    public String subirBytes(byte[] contenido, String key, String contentType) {
        if (s3Client == null) return "https://s3-simulado/" + key;
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket).key(key).contentType(contentType).build();
        s3Client.putObject(request, RequestBody.fromBytes(contenido));
        return obtenerUrlPublica(key);
    }

    public void eliminarArchivo(String key) {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Eliminación simulada para key: {}", key);
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /**
     * URL pre-firmada para descarga segura.
     */
    public String generarUrlPresignada(String key, int minutosExpiracion) {
        if (s3Client == null) return obtenerUrlPublica(key);
        try (S3Presigner presigner = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(region)).build()) {
            return presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(minutosExpiracion))
                    .getObjectRequest(r -> r.bucket(bucket).key(key))
                    .build()
            ).url().toString();
        }
    }

    /** Sobrecarga con 15 minutos por defecto (compatibilidad con código previo). */
    public String generarUrlPresignada(String key) {
        return generarUrlPresignada(key, 15);
    }

    /**
     * Lista todos los archivos de un trámite agrupados por departamento.
     * Retorna: Map<nombreDepartamento, List<UrlArchivo>>
     */
    public Map<String, List<String>> listarArchivosTramite(String empresaId,
                                                            String clienteId,
                                                            String tramiteId) {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Listado simulado vacío.");
            return new LinkedHashMap<>();
        }

        String prefijo = empresaId + "/" + clienteId + "/" + tramiteId + "/";

        ListObjectsV2Request req = ListObjectsV2Request.builder()
            .bucket(bucket).prefix(prefijo).build();

        ListObjectsV2Response resp = s3Client.listObjectsV2(req);

        Map<String, List<String>> resultado = new LinkedHashMap<>();
        for (S3Object obj : resp.contents()) {
            // key = empresaId/clienteId/tramiteId/departamento/timestamp_archivo
            String[] partes = obj.key().split("/");
            if (partes.length >= 5) {
                String depto = partes[3];
                resultado.computeIfAbsent(depto, k -> new ArrayList<>())
                         .add(obtenerUrlPublica(obj.key()));
            }
        }
        return resultado;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public String construirKey(String empresaId, String clienteId,
                                String tramiteId, String departamento,
                                String nombreArchivo) {
        String deptoSanitizado = departamento != null
            ? departamento.toLowerCase()
                          .replace(" ", "_")
                          .replace("/", "-")
                          .replaceAll("[^a-z0-9_\\-]", "")
            : "sin_departamento";

        String tramiteParte = tramiteId != null ? tramiteId : "libre";

        String archivoSanitizado = nombreArchivo != null
            ? nombreArchivo.replaceAll("[^a-zA-Z0-9._\\-]", "_")
            : "archivo";

        return String.format("%s/%s/%s/%s/%d_%s",
            empresaId, clienteId, tramiteParte,
            deptoSanitizado, System.currentTimeMillis(), archivoSanitizado);
    }

    public String obtenerUrlPublica(String key) {
        return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }

    /**
     * Extrae la key de S3 a partir de una URL pública.
     * Usado para eliminar o re-firmar archivos existentes.
     */
    public String extraerKeyDeUrl(String url) {
        return url.replace("https://" + bucket + ".s3.amazonaws.com/", "");
    }
}
