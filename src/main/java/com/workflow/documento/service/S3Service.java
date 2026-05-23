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

@Service
@Slf4j
public class S3Service {

    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${AWS_S3_BUCKET:wm-documentos}")
    private String bucket;

    @Value("${AWS_REGION:us-east-1}")
    private String region;

    public String subirArchivo(MultipartFile archivo, String empresaId,
                               String politicaId, String tramiteId) throws IOException {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Devolviendo URL simulada.");
            return "https://s3-simulado/" + construirKey(empresaId, politicaId, tramiteId, archivo.getOriginalFilename());
        }
        String key = construirKey(empresaId, politicaId, tramiteId, archivo.getOriginalFilename());
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket).key(key).contentType(archivo.getContentType()).build();
        s3Client.putObject(request, RequestBody.fromInputStream(archivo.getInputStream(), archivo.getSize()));
        return obtenerUrlPublica(key);
    }

    public String subirBytes(byte[] contenido, String key, String contentType) {
        if (s3Client == null) return "https://s3-simulado/" + key;
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket).key(key).contentType(contentType).build();
        s3Client.putObject(request, RequestBody.fromBytes(contenido));
        return obtenerUrlPublica(key);
    }

    public void eliminarArchivo(String key) {
        if (s3Client == null) return;
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public String generarUrlPresignada(String key) {
        if (s3Client == null) return obtenerUrlPublica(key);
        try (S3Presigner presigner = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(region)).build()) {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(r -> r.bucket(bucket).key(key)).build();
            return presigner.presignGetObject(presignRequest).url().toString();
        }
    }

    public String construirKey(String empresaId, String politicaId, String tramiteId, String nombreArchivo) {
        String base = empresaId + "/" + (politicaId != null ? politicaId : "libre") + "/";
        base += (tramiteId != null ? tramiteId : "libre") + "/";
        return base + System.currentTimeMillis() + "_" + nombreArchivo;
    }

    private String obtenerUrlPublica(String key) {
        return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }
}
