package com.workflow.documento.service;

import com.workflow.documento.dto.DocumentoRequest;
import com.workflow.documento.dto.DocumentoResponse;
import com.workflow.documento.model.*;
import com.workflow.documento.repository.AuditoriaDocumentoRepository;
import com.workflow.documento.repository.DocumentoRepository;
import com.workflow.tramite.repository.TramiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoService {

    private final DocumentoRepository documentoRepo;
    private final AuditoriaDocumentoRepository auditoriaRepo;
    private final S3Service s3Service;
    private final TramiteRepository tramiteRepo;
    private final com.workflow.documento.repository.DocumentoTramiteRepository documentoTramiteRepo;

    @Value("${onlyoffice.server.url:http://localhost:8088}")
    private String onlyofficeServerUrl;

    @Value("${onlyoffice.callback.url:http://localhost:8080/api/v1/onlyoffice/callback/}")
    private String onlyofficeCallbackUrl;

    @Value("${onlyoffice.jwt.secret:workflow-onlyoffice-secret-key}")
    private String onlyofficeJwtSecret;

    public String getOnlyofficeScriptUrl() {
        return onlyofficeServerUrl + "/web-apps/apps/api/documents/api.js";
    }

    public String getOnlyofficeCallbackUrl() {
        return onlyofficeCallbackUrl;
    }

    public DocumentoResponse subirDocumento(MultipartFile archivo, DocumentoRequest request,
                                             String usuarioId, String usuarioNombre) throws IOException {
        String carpeta = request.getCarpetaId() != null ? request.getCarpetaId() : "documentos";
        
        String clienteId = request.getClienteId();
        if ((clienteId == null || clienteId.isBlank() || "general".equalsIgnoreCase(clienteId)) && request.getTramiteId() != null) {
            clienteId = tramiteRepo.findById(request.getTramiteId())
                .map(com.workflow.tramite.model.Tramite::getClienteId)
                .orElse(null);
        }

        String tramiteOPoliticaId = request.getTramiteId();
        if (tramiteOPoliticaId == null || tramiteOPoliticaId.isBlank() || "general".equalsIgnoreCase(tramiteOPoliticaId)) {
            tramiteOPoliticaId = request.getPoliticaId();
        }

        String key = s3Service.construirKey(
            request.getEmpresaId() != null ? request.getEmpresaId() : "general",
            clienteId,
            tramiteOPoliticaId,
            carpeta,
            archivo.getOriginalFilename()
        );
        String url = s3Service.subirArchivoConKey(archivo, key);

        VersionDocumento primeraVersion = VersionDocumento.builder()
            .version(1).urlArchivo(url).s3Key(key)
            .fechaSubida(LocalDateTime.now()).subidoPorId(usuarioId).subidoPorNombre(usuarioNombre)
            .tamanioBytes(archivo.getSize()).build();

        PermisosDocumento permisos = request.getPermisos();
        if (permisos == null) {
            permisos = new PermisosDocumento(
                List.of("ADMIN_GENERAL", "TODOS"), List.of("ADMIN_GENERAL"), List.of("ADMIN_GENERAL"));
        }

        Documento doc = Documento.builder()
            .empresaId(request.getEmpresaId())
            .clienteId(clienteId)
            .nombre(request.getNombre() != null ? request.getNombre() : archivo.getOriginalFilename())
            .descripcion(request.getDescripcion()).tipoMime(archivo.getContentType())
            .urlArchivo(url).s3Key(key).tamanioBytes(archivo.getSize())
            .carpetaId(request.getCarpetaId()).politicaId(request.getPoliticaId()).tramiteId(request.getTramiteId())
            .etiquetas(request.getEtiquetas() != null ? request.getEtiquetas() : new ArrayList<>())
            .version(1).historialVersiones(new ArrayList<>(List.of(primeraVersion))).permisos(permisos)
            .creadoPorId(usuarioId).creadoPorNombre(usuarioNombre)
            .creadoEn(LocalDateTime.now()).modificadoEn(LocalDateTime.now()).build();

        Documento guardado = documentoRepo.save(doc);
        registrarAuditoria(guardado.getId(), usuarioId, usuarioNombre, "SUBIO", "Version 1 — " + archivo.getOriginalFilename());
        return mapToResponse(guardado);
    }

    public DocumentoResponse subirNuevaVersion(String documentoId, MultipartFile archivo,
                                                String usuarioId, String usuarioNombre) throws IOException {
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        String clienteId = doc.getClienteId();
        if ((clienteId == null || clienteId.isBlank() || "general".equalsIgnoreCase(clienteId)) && doc.getTramiteId() != null) {
            clienteId = tramiteRepo.findById(doc.getTramiteId())
                .map(com.workflow.tramite.model.Tramite::getClienteId)
                .orElse(null);
        }

        String tramiteOPoliticaId = doc.getTramiteId();
        if (tramiteOPoliticaId == null || tramiteOPoliticaId.isBlank() || "general".equalsIgnoreCase(tramiteOPoliticaId)) {
            tramiteOPoliticaId = doc.getPoliticaId();
        }

        String key = s3Service.construirKey(
            doc.getEmpresaId() != null ? doc.getEmpresaId() : "general",
            clienteId,
            tramiteOPoliticaId,
            "documentos",
            archivo.getOriginalFilename()
        );
        String url = s3Service.subirArchivoConKey(archivo, key);

        int nuevaVersion = doc.getVersion() + 1;
        VersionDocumento version = VersionDocumento.builder()
            .version(nuevaVersion).urlArchivo(url).s3Key(key)
            .fechaSubida(LocalDateTime.now()).subidoPorId(usuarioId).subidoPorNombre(usuarioNombre)
            .tamanioBytes(archivo.getSize()).build();

        if (doc.getHistorialVersiones() == null) doc.setHistorialVersiones(new ArrayList<>());
        doc.getHistorialVersiones().add(version);
        doc.setVersion(nuevaVersion);
        doc.setUrlArchivo(url);
        doc.setS3Key(key);
        doc.setModificadoEn(LocalDateTime.now());

        Documento actualizado = documentoRepo.save(doc);
        registrarAuditoria(doc.getId(), usuarioId, usuarioNombre, "SUBIO", "Nueva version: v" + nuevaVersion);
        return mapToResponse(actualizado);
    }

    public void guardarVersionDesdeBytes(String documentoId, byte[] contenido, String actor) {
        if (documentoId.startsWith("s3_dyn_")) {
            String base64Key = documentoId.substring(7);
            String key = new String(java.util.Base64.getUrlDecoder().decode(base64Key));
            s3Service.subirBytes(contenido, key, "application/octet-stream");
            log.info("[Callback] Actualizado archivo raw en S3: {}", key);
            return;
        }

        Optional<DocumentoTramite> dtOpt = documentoTramiteRepo.findById(documentoId);
        if (dtOpt.isPresent()) {
            DocumentoTramite dt = dtOpt.get();
            String key = dt.getS3Key() != null ? dt.getS3Key() : s3Service.extraerKeyDeUrl(dt.getUrlS3());
            s3Service.subirBytes(contenido, key, "application/octet-stream");
            log.info("[Callback] Actualizado DocumentoTramite en S3: {}", dt.getId());
            return;
        }

        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        String clienteId = doc.getClienteId();
        if ((clienteId == null || clienteId.isBlank() || "general".equalsIgnoreCase(clienteId)) && doc.getTramiteId() != null) {
            clienteId = tramiteRepo.findById(doc.getTramiteId())
                .map(com.workflow.tramite.model.Tramite::getClienteId)
                .orElse(null);
        }

        String tramiteOPoliticaId = doc.getTramiteId();
        if (tramiteOPoliticaId == null || tramiteOPoliticaId.isBlank() || "general".equalsIgnoreCase(tramiteOPoliticaId)) {
            tramiteOPoliticaId = doc.getPoliticaId();
        }

        String carpeta = doc.getCarpetaId() != null ? doc.getCarpetaId() : "documentos";

        String key = s3Service.construirKey(
            doc.getEmpresaId() != null ? doc.getEmpresaId() : "general",
            clienteId,
            tramiteOPoliticaId,
            carpeta,
            doc.getNombre()
        );
        String url = s3Service.subirBytes(contenido, key, doc.getTipoMime());

        int nuevaVersion = doc.getVersion() + 1;
        VersionDocumento version = VersionDocumento.builder()
            .version(nuevaVersion).urlArchivo(url).s3Key(key)
            .fechaSubida(LocalDateTime.now()).subidoPorId("system").subidoPorNombre(actor)
            .tamanioBytes((long) contenido.length).build();

        if (doc.getHistorialVersiones() == null) doc.setHistorialVersiones(new ArrayList<>());
        doc.getHistorialVersiones().add(version);
        doc.setVersion(nuevaVersion);
        doc.setUrlArchivo(url);
        doc.setS3Key(key);
        doc.setModificadoEn(LocalDateTime.now());
        documentoRepo.save(doc);
    }

    public List<DocumentoResponse> listar(String empresaId, String carpetaId, String politicaId, String tramiteId) {
        List<Documento> docs;
        if (tramiteId != null) {
            docs = documentoRepo.findByEmpresaIdAndTramiteIdAndEliminadoFalse(empresaId, tramiteId);
        } else if (politicaId != null) {
            docs = documentoRepo.findByEmpresaIdAndPoliticaIdAndEliminadoFalse(empresaId, politicaId);
        } else if (carpetaId != null) {
            docs = documentoRepo.findByEmpresaIdAndCarpetaIdAndEliminadoFalse(empresaId, carpetaId);
        } else {
            docs = documentoRepo.findByEmpresaIdAndEliminadoFalse(empresaId);
        }
        return docs.stream().map(this::mapToResponse).toList();
    }

    public List<DocumentoResponse> listarDocsOficina() {
        return documentoRepo.findByEsDocumentoOficinaAndEliminadoFalse(true)
            .stream().map(this::mapToResponse).toList();
    }

    public List<AuditoriaDocumento> obtenerAuditoria(String documentoId) {
        return auditoriaRepo.findByDocumentoIdOrderByFechaHoraDesc(documentoId);
    }

    public DocumentoResponse cambiarPermisos(String documentoId, PermisosDocumento permisos, String usuarioId) {
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        doc.setPermisos(permisos);
        doc.setModificadoEn(LocalDateTime.now());
        Documento actualizado = documentoRepo.save(doc);
        registrarAuditoria(documentoId, usuarioId, "", "CAMBIO_PERMISOS", "Permisos actualizados");
        return mapToResponse(actualizado);
    }

    public void eliminarDocumento(String documentoId, String usuarioId) {
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        if (doc.getS3Key() != null && !doc.getS3Key().isBlank()) {
            s3Service.eliminarArchivo(doc.getS3Key());
        }
        documentoRepo.deleteById(documentoId);
        registrarAuditoria(documentoId, usuarioId, "", "ELIMINADO", "Documento eliminado por el administrador");
    }

    public Map<String, Object> generarConfigOnlyOffice(String documentoId, String usuarioId,
                                                        String usuarioNombre, String modo) {
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        String callbackUrl = onlyofficeCallbackUrl + documentoId;
        String scriptUrl = onlyofficeServerUrl + "/web-apps/apps/api/documents/api.js";

        Map<String, Object> config = new HashMap<>();
        config.put("scriptUrl", scriptUrl);
        config.put("documentType", detectarTipoDoc(doc.getTipoMime(), doc.getTipoDocumento()));
        String s3KeyForUrl = doc.getS3Key() != null ? doc.getS3Key() : s3Service.extraerKeyDeUrl(doc.getUrlArchivo());
        String presignedUrl = s3Service.generarUrlPresignada(s3KeyForUrl, 1440);

        config.put("document", Map.of(
            "fileType", extraerExtension(doc.getNombre(), doc.getTipoDocumento()),
            "key", documentoId + "_v" + doc.getVersion(),
            "title", doc.getNombre(),
            "url", presignedUrl
        ));
        config.put("editorConfig", Map.of(
            "callbackUrl", callbackUrl,
            "mode", modo,
            "user", Map.of("id", usuarioId, "name", usuarioNombre),
            "lang", "es",
            "coEditing", Map.of("mode", "fast", "change", true)
        ));

        if (onlyofficeJwtSecret != null && !onlyofficeJwtSecret.isBlank()) {
            try {
                String token = io.jsonwebtoken.Jwts.builder()
                        .setClaims(config)
                        .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .compact();
                config.put("token", token);
            } catch (Exception e) {
                log.error("Error generando JWT para OnlyOffice", e);
            }
        }

        return config;
    }

    public Documento crearDocumentoOficina(String nombre, String tipo, String usuarioId) throws IOException {
        byte[] contenido = generarArchivoVacio(tipo);
        String mimeType = resolverMime(tipo);
        String nombreArchivo = nombre.endsWith("." + tipo) ? nombre : nombre + "." + tipo;
        long timestamp = System.currentTimeMillis();
        String key = "documentos_oficina/" + timestamp + "_" + nombreArchivo;

        String url = s3Service.subirBytes(contenido, key, mimeType);

        VersionDocumento primeraVersion = VersionDocumento.builder()
            .version(1).urlArchivo(url).s3Key(key)
            .fechaSubida(LocalDateTime.now()).subidoPorId(usuarioId).subidoPorNombre(usuarioId)
            .tamanioBytes((long) contenido.length).build();

        PermisosDocumento permisos = new PermisosDocumento(
            List.of("ADMIN_GENERAL", "TODOS"), List.of("ADMIN_GENERAL"), List.of("ADMIN_GENERAL"));

        Documento doc = Documento.builder()
            .empresaId("general")
            .nombre(nombreArchivo)
            .tipoMime(mimeType)
            .urlArchivo(url)
            .s3Key(key)
            .tamanioBytes((long) contenido.length)
            .version(1)
            .historialVersiones(new ArrayList<>(List.of(primeraVersion)))
            .permisos(permisos)
            .creadoPorId(usuarioId)
            .creadoPorNombre(usuarioId)
            .creadoEn(LocalDateTime.now())
            .modificadoEn(LocalDateTime.now())
            .esDocumentoOficina(true)
            .tipoDocumento(tipo)
            .build();

        Documento guardado = documentoRepo.save(doc);
        registrarAuditoria(guardado.getId(), usuarioId, usuarioId, "CREADO", "Documento de oficina creado: " + tipo);
        return guardado;
    }

    public Optional<Documento> buscarPorUrl(String url) {
        return documentoRepo.findByUrlArchivoAndEliminadoFalse(url);
    }


    private byte[] generarArchivoVacio(String tipo) throws IOException {
        return switch (tipo.toLowerCase()) {
            case "docx" -> {
                try (XWPFDocument doc = new XWPFDocument();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    doc.write(out);
                    yield out.toByteArray();
                }
            }
            case "xlsx" -> {
                try (Workbook wb = new XSSFWorkbook();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    wb.createSheet("Hoja1");
                    wb.write(out);
                    yield out.toByteArray();
                }
            }
            case "pptx" -> {
                try (XMLSlideShow ppt = new XMLSlideShow();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    ppt.write(out);
                    yield out.toByteArray();
                }
            }
            default -> new byte[0];
        };
    }

    private String resolverMime(String tipo) {
        return switch (tipo.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "odt"  -> "application/vnd.oasis.opendocument.text";
            case "ods"  -> "application/vnd.oasis.opendocument.spreadsheet";
            case "odp"  -> "application/vnd.oasis.opendocument.presentation";
            case "csv"  -> "text/csv";
            case "txt"  -> "text/plain";
            default     -> "application/octet-stream";
        };
    }

    private String detectarTipoDoc(String mime, String tipoDocumento) {
        // Si el tipo de documento está guardado explícitamente, usarlo primero
        if (tipoDocumento != null) {
            return switch (tipoDocumento.toLowerCase()) {
                case "xlsx", "xls", "ods", "ots", "csv" -> "cell";
                case "pptx", "ppt", "odp", "otp"        -> "slide";
                default                                  -> "word";
            };
        }
        if (mime == null) return "word";
        return switch (mime) {
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel",
                 "text/csv",
                 "application/vnd.oasis.opendocument.spreadsheet" -> "cell";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.oasis.opendocument.presentation" -> "slide";
            default -> "word";
        };
    }

    private String extraerExtension(String nombre, String tipoDocumento) {
        if (tipoDocumento != null && !tipoDocumento.isBlank()) return tipoDocumento.toLowerCase();
        if (nombre == null || !nombre.contains(".")) return "docx";
        return nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase();
    }

    private void registrarAuditoria(String docId, String userId, String nombre, String accion, String detalle) {
        auditoriaRepo.save(AuditoriaDocumento.builder()
            .documentoId(docId).usuarioId(userId).usuarioNombre(nombre)
            .accion(accion).detalles(detalle).fechaHora(LocalDateTime.now()).build());
    }

    private DocumentoResponse mapToResponse(Documento doc) {
        return DocumentoResponse.builder()
            .id(doc.getId()).empresaId(doc.getEmpresaId()).clienteId(doc.getClienteId()).nombre(doc.getNombre())
            .descripcion(doc.getDescripcion()).tipoMime(doc.getTipoMime())
            .urlArchivo(doc.getUrlArchivo()).s3Key(doc.getS3Key()).tamanioBytes(doc.getTamanioBytes())
            .carpetaId(doc.getCarpetaId()).politicaId(doc.getPoliticaId()).tramiteId(doc.getTramiteId())
            .etiquetas(doc.getEtiquetas()).version(doc.getVersion())
            .historialVersiones(doc.getHistorialVersiones()).permisos(doc.getPermisos())
            .creadoPorId(doc.getCreadoPorId()).creadoPorNombre(doc.getCreadoPorNombre())
            .creadoEn(doc.getCreadoEn()).modificadoEn(doc.getModificadoEn())
            .esDocumentoOficina(doc.isEsDocumentoOficina())
            .tipoDocumento(doc.getTipoDocumento())
            .build();
    }
}
