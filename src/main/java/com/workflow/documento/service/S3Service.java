package com.workflow.documento.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import com.workflow.usuario.repository.UsuarioRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.departamento.model.Departamento;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.politica.model.Politica;

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

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private TramiteRepository tramiteRepository;

    @Autowired(required = false)
    private NodoRepository nodoRepository;

    @Autowired(required = false)
    private DepartamentoRepository departamentoRepository;

    @Autowired(required = false)
    private PoliticaRepository politicaRepository;

    @Value("${AWS_S3_BUCKET:wm-documentos}")
    private String bucket;

    @Value("${AWS_ACCESS_KEY_ID:}")
    private String accessKey;

    @Value("${AWS_SECRET_ACCESS_KEY:}")
    private String secretKey;

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

    /**
     * Sube un archivo usando una key ya construida (sin regenerar timestamp).
     * Usar este método cuando se necesita conocer la key antes de subir.
     */
    public String subirArchivoConKey(MultipartFile archivo, String key) throws IOException {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Devolviendo URL simulada para key: {}", key);
            return "https://s3-simulado/" + key;
        }
        String contentType = archivo.getContentType() != null ? archivo.getContentType() : "application/octet-stream";
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentDisposition("inline")
            .build();
        s3Client.putObject(request,
            RequestBody.fromInputStream(archivo.getInputStream(), archivo.getSize()));
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
        // Trim defensivo identico al de AwsS3Config: elimina \r (CRLF Windows) y espacios
        String cleanAccessKey = accessKey != null ? accessKey.trim() : "";
        String cleanSecretKey = secretKey != null ? secretKey.trim() : "";
        String cleanRegion    = region    != null ? region.trim()    : "us-east-1";
        S3Configuration s3Config = S3Configuration.builder()
            .useArnRegionEnabled(true)
            .build();
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(software.amazon.awssdk.regions.Region.of(cleanRegion))
                .serviceConfiguration(s3Config);
        if (!cleanAccessKey.isBlank() && !cleanSecretKey.isBlank()) {
            presignerBuilder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cleanAccessKey, cleanSecretKey)
                )
            );
        }
        try (S3Presigner presigner = presignerBuilder.build()) {
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

    public Map<String, List<String>> listarArchivosTramite(String empresaId,
                                                            String clienteId,
                                                            String tramiteId) {
        if (s3Client == null) {
            log.warn("S3Client no configurado. Listado simulado vacío.");
            return new LinkedHashMap<>();
        }

        String cId = (clienteId != null && !clienteId.isBlank()) ? clienteId : "sin_cliente_id";
        String tId = (tramiteId != null && !tramiteId.isBlank()) ? tramiteId : "sin_tramite_id";
        String prefijo = cId + "/" + tId + "/";

        ListObjectsV2Request req = ListObjectsV2Request.builder()
            .bucket(bucket).prefix(prefijo).build();

        ListObjectsV2Response resp = s3Client.listObjectsV2(req);

        Map<String, List<String>> resultado = new LinkedHashMap<>();
        for (S3Object obj : resp.contents()) {
            // key = clienteId/tramiteId/departamentoId/timestamp_archivo
            String[] partes = obj.key().split("/");
            if (partes.length >= 4) {
                String depto = partes[2];
                resultado.computeIfAbsent(depto, k -> new ArrayList<>())
                         .add(obtenerUrlPublica(obj.key()));
            }
        }
        return resultado;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Retorna true si el S3Client fue inyectado correctamente (aws.s3.enabled=true y credenciales validas). */
    public boolean isS3Disponible() {
        return s3Client != null;
    }

    public String construirKey(String empresaId, String clienteId,
                                String tramiteId, String departamento,
                                String nombreArchivo) {
                                
        // 0. Limpiar valores inválidos desde el frontend
        if ("undefined".equals(clienteId) || "null".equals(clienteId)) clienteId = null;
        if ("undefined".equals(tramiteId) || "null".equals(tramiteId)) tramiteId = null;
        if ("undefined".equals(departamento) || "null".equals(departamento)) departamento = null;
        
        // 1. Intentar recuperar tramiteId desde el clienteId si hay un trámite activo
        if ((tramiteId == null || tramiteId.isBlank()) && clienteId != null && !clienteId.isBlank() && tramiteRepository != null) {
            List<Tramite> tramitesCliente = tramiteRepository.findByClienteIdOrderByIniciadoEnDesc(clienteId);
            Tramite activo = tramitesCliente.stream()
                .filter(t -> "PENDIENTE".equals(t.getEstadoGeneral()) || "EN_PROCESO".equals(t.getEstadoGeneral()))
                .findFirst().orElse(null);
            if (activo != null) {
                tramiteId = activo.getId();
            }
        }

        // 2. Si ya tenemos tramiteId, intentar recuperar el resto del contexto (clienteId y departamento)
        if (tramiteId != null && !tramiteId.isBlank() && tramiteRepository != null) {
            Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
            if (tramite != null) {
                // Recuperar clienteId si falta
                if (clienteId == null || clienteId.isBlank() || "general".equalsIgnoreCase(clienteId)) {
                    clienteId = tramite.getClienteId();
                }
                
                // Recuperar departamento si falta o es genérico
                boolean faltaDepto = departamento == null || departamento.isBlank() || "documentos".equals(departamento);
                if (faltaDepto && tramite.getNodoActualId() != null && nodoRepository != null) {
                    Nodo nodo = nodoRepository.findById(tramite.getNodoActualId()).orElse(null);
                    if (nodo != null && nodo.getDepartamentoId() != null && departamentoRepository != null) {
                        Departamento depto = departamentoRepository.findById(nodo.getDepartamentoId()).orElse(null);
                        departamento = depto != null ? depto.getNombre() : nodo.getDepartamentoId();
                    }
                }
            }
        }

        String cId = (clienteId != null && !clienteId.isBlank()) ? clienteId : "sin_cliente_id";
        String tId = (tramiteId != null && !tramiteId.isBlank()) ? tramiteId : "sin_tramite_id";
        String dId = (departamento != null && !departamento.isBlank()) ? departamento : "sin_departamento_id";

        String archivoSanitizado = nombreArchivo != null
            ? nombreArchivo.replaceAll("[^a-zA-Z0-9._\\-]", "_")
            : "archivo";

        return String.format("%s/%s/%s/%d_%s",
            cId, tId, dId,
            System.currentTimeMillis(), archivoSanitizado);
    }

    public String obtenerUrlPublica(String key) {
        return "https://" + bucket + ".s3.amazonaws.com/" + key;
    }

    /**
     * Extrae la key de S3 a partir de cualquier formato de URL de S3.
     * Soporta:
     *  - https://{bucket}.s3.amazonaws.com/{key}
     *  - https://{bucket}.s3.{region}.amazonaws.com/{key}
     *  - https://s3.amazonaws.com/{bucket}/{key}
     *  - URLs presignadas (con parámetros ?X-Amz-...) — extrae solo la key
     */
    public String extraerKeyDeUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            // 1. Quitar query string (parámetros de firma X-Amz-*)
            String urlBase = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;

            // 2. Formato virtual-hosted: https://{bucket}.s3.amazonaws.com/{key}
            //    o regional: https://{bucket}.s3.{region}.amazonaws.com/{key}
            String bucketPrefix = bucket + ".s3.";
            if (urlBase.contains(bucketPrefix)) {
                int idxBucket = urlBase.indexOf(bucketPrefix);
                String afterBucket = urlBase.substring(idxBucket + bucketPrefix.length());
                // afterBucket = "amazonaws.com/{key}" o "{region}.amazonaws.com/{key}"
                int dotAmazon = afterBucket.indexOf(".amazonaws.com");
                if (dotAmazon >= 0) {
                    String afterHost = afterBucket.substring(dotAmazon + ".amazonaws.com".length());
                    if (afterHost.startsWith("/")) afterHost = afterHost.substring(1);
                    if (!afterHost.isBlank()) return afterHost;
                }
            }

            // 3. Formato path-style: https://s3.amazonaws.com/{bucket}/{key}
            String pathStylePrefix = "s3.amazonaws.com/" + bucket + "/";
            if (urlBase.contains(pathStylePrefix)) {
                return urlBase.substring(urlBase.indexOf(pathStylePrefix) + pathStylePrefix.length());
            }

            // 4. Fallback: extraer path de la URI y quitar el bucket si aparece al inicio
            java.net.URI uri = new java.net.URI(urlBase);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) path = path.substring(1);
            if (path != null && path.startsWith(bucket + "/")) {
                path = path.substring(bucket.length() + 1);
            }
            return path != null ? path : "";
        } catch (Exception e) {
            log.warn("extraerKeyDeUrl: no se pudo parsear la URL '{}': {}", url, e.getMessage());
            // Último recurso: reemplazado simple
            return url.replace("https://" + bucket + ".s3.amazonaws.com/", "")
                      .replaceAll("\\?.*$", ""); // quitar query string si existe
        }
    }

}
