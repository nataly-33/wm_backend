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

        String nombreCliente = resolverNombreCliente(clienteId, tramiteId);
        String nombreTramite = resolverNombreTramite(tramiteId, null);
        String prefijo = nombreCliente + "/" + nombreTramite + "/";

        ListObjectsV2Request req = ListObjectsV2Request.builder()
            .bucket(bucket).prefix(prefijo).build();

        ListObjectsV2Response resp = s3Client.listObjectsV2(req);

        Map<String, List<String>> resultado = new LinkedHashMap<>();
        for (S3Object obj : resp.contents()) {
            // key = nombreCliente/nombreTramite/nombreDepartamento/timestamp_archivo
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
        String nombreCliente = resolverNombreCliente(clienteId, tramiteId);
        String nombreTramite = resolverNombreTramite(tramiteId, null);
        String nombreDepto = resolverNombreDepartamento(departamento, tramiteId);

        String archivoSanitizado = nombreArchivo != null
            ? nombreArchivo.replaceAll("[^a-zA-Z0-9._\\-]", "_")
            : "archivo";

        return String.format("%s/%s/%s/%d_%s",
            nombreCliente, nombreTramite, nombreDepto,
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

    private String resolverNombreCliente(String clienteId, String tramiteId) {
        String nombreCliente = null;

        // 1. Si tenemos clienteId válido (no null, no vacío, no "general", no "documentos")
        if (usuarioRepository != null && clienteId != null && !clienteId.isBlank()
                && !"general".equalsIgnoreCase(clienteId) && !"documentos".equalsIgnoreCase(clienteId)) {
            try {
                nombreCliente = usuarioRepository.findById(clienteId)
                    .map(u -> {
                        String n = u.getNombre();
                        return (n != null && !n.isBlank()) ? n : null;
                    })
                    .orElse(null);
                if (nombreCliente != null) {
                    log.debug("[S3] Cliente resuelto por ID {}: {}", clienteId, nombreCliente);
                }
            } catch (Exception e) {
                log.warn("[S3] Error buscando cliente por ID {}: {}", clienteId, e.getMessage());
            }
        }

        // 2. Si no se pudo obtener por clienteId, pero tenemos tramiteId → sacarlo del trámite
        if ((nombreCliente == null || nombreCliente.isBlank())
                && tramiteRepository != null && usuarioRepository != null
                && tramiteId != null && !tramiteId.isBlank()
                && !"general".equalsIgnoreCase(tramiteId) && !"documentos".equalsIgnoreCase(tramiteId)
                && tramiteId.length() == 24) {
            try {
                Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
                if (tramite != null && tramite.getClienteId() != null && !tramite.getClienteId().isBlank()) {
                    nombreCliente = usuarioRepository.findById(tramite.getClienteId())
                        .map(u -> {
                            String n = u.getNombre();
                            return (n != null && !n.isBlank()) ? n : null;
                        })
                        .orElse(null);
                    if (nombreCliente != null) {
                        log.debug("[S3] Cliente resuelto desde tramite {}: {}", tramiteId, nombreCliente);
                    }
                }
                // Si el trámite tiene iniciadoPor y no clienteId, usar ese
                if ((nombreCliente == null || nombreCliente.isBlank())
                        && tramite != null && tramite.getIniciadoPor() != null && !tramite.getIniciadoPor().isBlank()) {
                    nombreCliente = usuarioRepository.findById(tramite.getIniciadoPor())
                        .map(u -> {
                            String n = u.getNombre();
                            return (n != null && !n.isBlank()) ? n : null;
                        })
                        .orElse(null);
                    if (nombreCliente != null) {
                        log.debug("[S3] Cliente resuelto por iniciadoPor en tramite {}: {}", tramiteId, nombreCliente);
                    }
                }
            } catch (Exception e) {
                log.warn("[S3] Error buscando cliente a partir de tramite ID {}: {}", tramiteId, e.getMessage());
            }
        }

        // 3. Fallback: si el clienteId parece ser un nombre legible (no un ObjectId de 24 hex)
        if ((nombreCliente == null || nombreCliente.isBlank())
                && clienteId != null && !clienteId.isBlank()
                && !"general".equalsIgnoreCase(clienteId) && !"documentos".equalsIgnoreCase(clienteId)
                && clienteId.length() != 24) {
            nombreCliente = clienteId; // ya es un nombre legible
        }

        return sanitizarComponenteRuta(nombreCliente, "cliente_sin_nombre");
    }

    private String resolverNombreTramite(String tramiteId, String politicaId) {
        String nombreTramite = null;

        // 1. Si tenemos tramiteId válido (ObjectId de 24 hex), buscamos en tramiteRepository
        if (tramiteRepository != null && tramiteId != null && !tramiteId.isBlank()
                && !"general".equalsIgnoreCase(tramiteId) && !"documentos".equalsIgnoreCase(tramiteId)
                && tramiteId.length() == 24) {
            try {
                Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
                if (tramite != null) {
                    // Primero intentar título del trámite
                    String titulo = tramite.getTitulo();
                    if (titulo != null && !titulo.isBlank()) {
                        nombreTramite = titulo;
                        log.debug("[S3] Nombre trámite resuelto por título: {}", nombreTramite);
                    }
                    // Si no hay título, buscar nombre de la política
                    if ((nombreTramite == null || nombreTramite.isBlank())
                            && politicaRepository != null && tramite.getPoliticaId() != null
                            && !tramite.getPoliticaId().isBlank()) {
                        nombreTramite = politicaRepository.findById(tramite.getPoliticaId())
                            .map(Politica::getNombre)
                            .orElse(null);
                        if (nombreTramite != null) {
                            log.debug("[S3] Nombre trámite resuelto por política {}: {}", tramite.getPoliticaId(), nombreTramite);
                        }
                    }
                    // Último recurso: abreviación del ID del trámite
                    if (nombreTramite == null || nombreTramite.isBlank()) {
                        nombreTramite = "tramite_" + tramiteId.substring(0, Math.min(6, tramiteId.length()));
                    }
                } else if (politicaRepository != null) {
                    // Puede ser un ID de política directamente
                    nombreTramite = politicaRepository.findById(tramiteId)
                        .map(Politica::getNombre)
                        .orElse(null);
                    if (nombreTramite != null) {
                        log.debug("[S3] ID {} resuelto como política: {}", tramiteId, nombreTramite);
                    }
                }
            } catch (Exception e) {
                log.warn("[S3] Error buscando tramite/politica por ID {}: {}", tramiteId, e.getMessage());
            }
        }

        // 2. Si no se pudo obtener por tramiteId, pero tenemos politicaId
        if ((nombreTramite == null || nombreTramite.isBlank())
                && politicaRepository != null && politicaId != null && !politicaId.isBlank()
                && !"general".equalsIgnoreCase(politicaId) && !"documentos".equalsIgnoreCase(politicaId)
                && politicaId.length() == 24) {
            try {
                nombreTramite = politicaRepository.findById(politicaId)
                    .map(Politica::getNombre)
                    .orElse(null);
                if (nombreTramite != null) {
                    log.debug("[S3] Nombre trámite resuelto por politicaId {}: {}", politicaId, nombreTramite);
                }
            } catch (Exception e) {
                log.warn("[S3] Error buscando politica por ID {}: {}", politicaId, e.getMessage());
            }
        }

        // 3. Fallback: si tramiteId parece ser un nombre legible (no ObjectId)
        if ((nombreTramite == null || nombreTramite.isBlank())
                && tramiteId != null && !tramiteId.isBlank()
                && !"general".equalsIgnoreCase(tramiteId) && !"documentos".equalsIgnoreCase(tramiteId)
                && tramiteId.length() != 24) {
            nombreTramite = tramiteId; // ya es un nombre legible
        }

        return sanitizarComponenteRuta(nombreTramite, "tramite_sin_nombre");
    }

    private String resolverNombreDepartamento(String departamento, String tramiteId) {
        String nombreDepto = null;

        // 1. Si nos pasaron un departamento y parece ser un ObjectId (24 hex), buscarlo en BD
        if (departamentoRepository != null && departamento != null && !departamento.isBlank()
                && !"general".equalsIgnoreCase(departamento) && !"documentos".equalsIgnoreCase(departamento)
                && departamento.length() == 24) {
            try {
                nombreDepto = departamentoRepository.findById(departamento)
                    .map(Departamento::getNombre)
                    .orElse(null);
                if (nombreDepto != null) {
                    log.debug("[S3] Departamento resuelto por ID {}: {}", departamento, nombreDepto);
                }
            } catch (Exception e) {
                log.warn("[S3] Error buscando departamento por ID {}: {}", departamento, e.getMessage());
            }
        }

        // 2. Si no se resolvió como ObjectId, pero el valor parece un nombre legible
        // (no es null/vacío/general/documentos y no tiene 24 chars de hex)
        if ((nombreDepto == null || nombreDepto.isBlank())
                && departamento != null && !departamento.isBlank()
                && !"general".equalsIgnoreCase(departamento) && !"documentos".equalsIgnoreCase(departamento)
                && departamento.length() != 24) {
            nombreDepto = departamento; // ya es un nombre legible
            log.debug("[S3] Departamento tratado como nombre directo: {}", nombreDepto);
        }

        // 3. Si sigue sin resolverse y tenemos tramiteId, buscar el departamento del nodo actual
        if ((nombreDepto == null || nombreDepto.isBlank())
                && tramiteRepository != null && nodoRepository != null && departamentoRepository != null
                && tramiteId != null && !tramiteId.isBlank()
                && !"general".equalsIgnoreCase(tramiteId) && !"documentos".equalsIgnoreCase(tramiteId)
                && tramiteId.length() == 24) {
            try {
                Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
                if (tramite != null && tramite.getNodoActualId() != null && !tramite.getNodoActualId().isBlank()) {
                    Nodo nodo = nodoRepository.findById(tramite.getNodoActualId()).orElse(null);
                    if (nodo != null && nodo.getDepartamentoId() != null && !nodo.getDepartamentoId().isBlank()) {
                        nombreDepto = departamentoRepository.findById(nodo.getDepartamentoId())
                            .map(Departamento::getNombre)
                            .orElse(null);
                        if (nombreDepto != null) {
                            log.debug("[S3] Departamento resuelto por nodo actual del trámite {}: {}", tramiteId, nombreDepto);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[S3] Error buscando departamento del nodo actual del tramite ID {}: {}", tramiteId, e.getMessage());
            }
        }

        return sanitizarComponenteRuta(nombreDepto, "departamento_general");
    }

    private String sanitizarComponenteRuta(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String limpio = input.trim();
        // Evitar usar "general" o "documentos" como componente de ruta real
        if ("general".equalsIgnoreCase(limpio) || "documentos".equalsIgnoreCase(limpio)) {
            return fallback;
        }
        return limpio
            .replace("/", "-")       // No queremos barras diagonales intermedias
            .replace("\\", "-")      // No queremos barras invertidas
            .replaceAll("[*?:\"<>|]", "") // Quitar caracteres prohibidos en sistemas de archivos comunes
            .trim();
    }
}
