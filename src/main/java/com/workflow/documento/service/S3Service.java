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
     * Extrae la key de S3 a partir de una URL pública.
     * Usado para eliminar o re-firmar archivos existentes.
     */
    public String extraerKeyDeUrl(String url) {
        return url.replace("https://" + bucket + ".s3.amazonaws.com/", "");
    }

    private String resolverNombreCliente(String clienteId, String tramiteId) {
        String nombreCliente = null;

        // 1. Si tenemos clienteId
        if (usuarioRepository != null && clienteId != null && !clienteId.isBlank() && !"general".equalsIgnoreCase(clienteId)) {
            try {
                nombreCliente = usuarioRepository.findById(clienteId)
                    .map(Usuario::getNombre)
                    .orElse(null);
            } catch (Exception e) {
                log.error("Error buscando cliente por ID {}: {}", clienteId, e.getMessage());
            }
        }

        // 2. Si no se pudo obtener, pero tenemos tramiteId
        if ((nombreCliente == null || nombreCliente.isBlank() || "general".equalsIgnoreCase(nombreCliente))
                && tramiteRepository != null && usuarioRepository != null
                && tramiteId != null && !tramiteId.isBlank() && !"general".equalsIgnoreCase(tramiteId)) {
            try {
                Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
                if (tramite != null && tramite.getClienteId() != null) {
                    nombreCliente = usuarioRepository.findById(tramite.getClienteId())
                        .map(Usuario::getNombre)
                        .orElse(null);
                }
            } catch (Exception e) {
                log.error("Error buscando cliente a partir de tramite ID {}: {}", tramiteId, e.getMessage());
            }
        }

        // 3. Fallback: si no es nulo pero no existe en BD, tal vez ya sea el nombre
        if (nombreCliente == null || nombreCliente.isBlank() || "general".equalsIgnoreCase(nombreCliente)) {
            if (clienteId != null && !clienteId.isBlank() && !"general".equalsIgnoreCase(clienteId) && clienteId.length() != 24) {
                nombreCliente = clienteId;
            }
        }

        return sanitizarComponenteRuta(nombreCliente, "cliente_desconocido");
    }

    private String resolverNombreTramite(String tramiteId, String politicaId) {
        String nombreTramite = null;

        // 1. Si tenemos tramiteId, buscamos en tramiteRepository
        if (tramiteRepository != null && tramiteId != null && !tramiteId.isBlank() && !"general".equalsIgnoreCase(tramiteId)) {
            try {
                Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
                if (tramite != null) {
                    nombreTramite = tramite.getTitulo();
                    if ((nombreTramite == null || nombreTramite.isBlank()) && politicaRepository != null && tramite.getPoliticaId() != null) {
                        nombreTramite = politicaRepository.findById(tramite.getPoliticaId())
                            .map(Politica::getNombre)
                            .orElse(null);
                    }
                } else if (politicaRepository != null) {
                    // Si no existe como tramite, podria ser un ID de politica
                    nombreTramite = politicaRepository.findById(tramiteId)
                        .map(Politica::getNombre)
                        .orElse(null);
                }
            } catch (Exception e) {
                log.error("Error buscando tramite o politica por ID {}: {}", tramiteId, e.getMessage());
            }
        }

        // 2. Si no se pudo obtener, pero tenemos politicaId
        if ((nombreTramite == null || nombreTramite.isBlank() || "general".equalsIgnoreCase(nombreTramite))
                && politicaRepository != null && politicaId != null && !politicaId.isBlank() && !"general".equalsIgnoreCase(politicaId)) {
            try {
                nombreTramite = politicaRepository.findById(politicaId)
                    .map(Politica::getNombre)
                    .orElse(null);
            } catch (Exception e) {
                log.error("Error buscando politica por ID {}: {}", politicaId, e.getMessage());
            }
        }

        // 3. Fallback: si no es nulo y no se encontro, tal vez ya sea el nombre
        if (nombreTramite == null || nombreTramite.isBlank() || "general".equalsIgnoreCase(nombreTramite)) {
            if (tramiteId != null && !tramiteId.isBlank() && !"general".equalsIgnoreCase(tramiteId) && tramiteId.length() != 24) {
                nombreTramite = tramiteId;
            }
        }

        return sanitizarComponenteRuta(nombreTramite, "tramite_desconocido");
    }

    private String resolverNombreDepartamento(String departamento, String tramiteId) {
        String nombreDepto = null;

        // 1. Si nos pasaron un departamento/carpeta, intentamos ver si existe como ID en la BD
        if (departamentoRepository != null && departamento != null && !departamento.isBlank()
                && !"general".equalsIgnoreCase(departamento) && !"documentos".equalsIgnoreCase(departamento)) {
            try {
                nombreDepto = departamentoRepository.findById(departamento)
                    .map(Departamento::getNombre)
                    .orElse(null);
            } catch (Exception e) {
                log.error("Error buscando departamento por ID {}: {}", departamento, e.getMessage());
            }
        }

        // 2. Si no se resolvio como ID de departamento, pero no es nulo/vacio/general/documentos,
        // lo tratamos como el nombre directo del departamento.
        if ((nombreDepto == null || nombreDepto.isBlank()) && departamento != null && !departamento.isBlank()
                && !"general".equalsIgnoreCase(departamento) && !"documentos".equalsIgnoreCase(departamento)) {
            nombreDepto = departamento;
        }

        // 3. Si sigue siendo nulo/vacio/general/documentos y tenemos tramiteId,
        // buscamos el departamento del nodo actual del tramite
        if ((nombreDepto == null || nombreDepto.isBlank() || "general".equalsIgnoreCase(nombreDepto) || "documentos".equalsIgnoreCase(nombreDepto))
                && tramiteRepository != null && nodoRepository != null && departamentoRepository != null
                && tramiteId != null && !tramiteId.isBlank() && !"general".equalsIgnoreCase(tramiteId)) {
            try {
                Tramite tramite = tramiteRepository.findById(tramiteId).orElse(null);
                if (tramite != null && tramite.getNodoActualId() != null) {
                    Nodo nodo = nodoRepository.findById(tramite.getNodoActualId()).orElse(null);
                    if (nodo != null && nodo.getDepartamentoId() != null) {
                        nombreDepto = departamentoRepository.findById(nodo.getDepartamentoId())
                            .map(Departamento::getNombre)
                            .orElse(null);
                    }
                }
            } catch (Exception e) {
                log.error("Error buscando departamento del nodo actual del tramite ID {}: {}", tramiteId, e.getMessage());
            }
        }

        // 4. Si no pudimos resolverlo pero el departamento original no estaba vacio (por ej: "documentos"),
        // lo conservamos en lugar de poner "sin_departamento"
        if ((nombreDepto == null || nombreDepto.isBlank() || "general".equalsIgnoreCase(nombreDepto))
                && departamento != null && !departamento.isBlank() && !"general".equalsIgnoreCase(departamento)) {
            nombreDepto = departamento;
        }

        return sanitizarComponenteRuta(nombreDepto, "sin_departamento");
    }

    private String sanitizarComponenteRuta(String input, String fallback) {
        if (input == null || input.isBlank() || "general".equalsIgnoreCase(input.trim())) {
            return fallback;
        }
        return input.trim()
            .replace("/", "-")       // No queremos barras diagonales intermedias
            .replace("\\", "-")      // No queremos barras invertidas
            .replaceAll("[*?:\"<>|]", "") // Quitar caracteres prohibidos en sistemas de archivos comunes
            .trim();
    }
}
