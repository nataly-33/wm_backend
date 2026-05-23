package com.workflow.documento.service;

import com.workflow.documento.dto.DocumentoRequest;
import com.workflow.documento.dto.DocumentoResponse;
import com.workflow.documento.model.*;
import com.workflow.documento.repository.AuditoriaDocumentoRepository;
import com.workflow.documento.repository.DocumentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoService {

    private final DocumentoRepository documentoRepo;
    private final AuditoriaDocumentoRepository auditoriaRepo;
    private final S3Service s3Service;

    public DocumentoResponse subirDocumento(MultipartFile archivo, DocumentoRequest request,
                                             String usuarioId, String usuarioNombre) throws IOException {
        String url = s3Service.subirArchivo(archivo, request.getEmpresaId(), request.getPoliticaId(), request.getTramiteId());
        String key = s3Service.construirKey(request.getEmpresaId(), request.getPoliticaId(), request.getTramiteId(), archivo.getOriginalFilename());

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

        String url = s3Service.subirArchivo(archivo, doc.getEmpresaId(), doc.getPoliticaId(), doc.getTramiteId());
        String key = s3Service.construirKey(doc.getEmpresaId(), doc.getPoliticaId(), doc.getTramiteId(), archivo.getOriginalFilename());

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
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        String key = doc.getEmpresaId() + "/" + (doc.getPoliticaId() != null ? doc.getPoliticaId() : "libre")
            + "/" + System.currentTimeMillis() + "_" + doc.getNombre();
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

    public Map<String, Object> generarConfigOnlyOffice(String documentoId, String usuarioId,
                                                        String usuarioNombre, String modo) {
        Documento doc = documentoRepo.findById(documentoId)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));

        String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");
        String callbackUrl = backendUrl + "/api/v1/onlyoffice/callback/" + documentoId;

        return Map.of(
            "documentType", detectarTipoDoc(doc.getTipoMime()),
            "document", Map.of(
                "fileType", extraerExtension(doc.getNombre()),
                "key", documentoId + "_v" + doc.getVersion(),
                "title", doc.getNombre(),
                "url", doc.getUrlArchivo()
            ),
            "editorConfig", Map.of(
                "callbackUrl", callbackUrl,
                "mode", modo,
                "user", Map.of("id", usuarioId, "name", usuarioNombre),
                "lang", "es"
            )
        );
    }

    private String detectarTipoDoc(String mime) {
        if (mime == null) return "word";
        return switch (mime) {
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" -> "cell";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "slide";
            default -> "word";
        };
    }

    private String extraerExtension(String nombre) {
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
            .id(doc.getId()).empresaId(doc.getEmpresaId()).nombre(doc.getNombre())
            .descripcion(doc.getDescripcion()).tipoMime(doc.getTipoMime())
            .urlArchivo(doc.getUrlArchivo()).s3Key(doc.getS3Key()).tamanioBytes(doc.getTamanioBytes())
            .carpetaId(doc.getCarpetaId()).politicaId(doc.getPoliticaId()).tramiteId(doc.getTramiteId())
            .etiquetas(doc.getEtiquetas()).version(doc.getVersion())
            .historialVersiones(doc.getHistorialVersiones()).permisos(doc.getPermisos())
            .creadoPorId(doc.getCreadoPorId()).creadoPorNombre(doc.getCreadoPorNombre())
            .creadoEn(doc.getCreadoEn()).modificadoEn(doc.getModificadoEn()).build();
    }
}
