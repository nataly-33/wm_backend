package com.workflow.archivo.service;

import com.workflow.documento.model.DocumentoTramite;
import com.workflow.documento.repository.DocumentoTramiteRepository;
import com.workflow.documento.service.S3Service;
import com.workflow.formulario.model.LlenadoPor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class ArchivoService {

    @Value("${archivos.upload-dir:./uploads}")
    private String uploadDir;

    @Autowired(required = false)
    private S3Service s3Service;

    @Autowired(required = false)
    private DocumentoTramiteRepository documentoTramiteRepo;

    @PostConstruct
    public void init() {
        log.info("ArchivoService inicializado. Almacenamiento local configurado en: {}", uploadDir);
    }

    public String subirArchivo(MultipartFile archivo) throws IOException {
        return subirArchivo(archivo, null, null, null, null);
    }

    public String subirArchivo(MultipartFile archivo,
                               String empresaId,
                               String clienteId,
                               String tramiteId,
                               String nombreDepartamento) throws IOException {
        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            nombreOriginal = "archivo";
        }

        // 1. Si S3 esta configurado, subir alli (prioridad maxima)
        if (s3Service != null && s3Service.isS3Disponible()) {
            String empId = (empresaId != null && !empresaId.isBlank()) ? empresaId : "general";
            String clId = (clienteId != null && !clienteId.isBlank()) ? clienteId : "general";
            String tramId = (tramiteId != null && !tramiteId.isBlank()) ? tramiteId : "general";
            String depto = (nombreDepartamento != null && !nombreDepartamento.isBlank()) ? nombreDepartamento : "general";

            String key = s3Service.construirKey(empId, clId, tramId, depto, nombreOriginal);
            String url = s3Service.subirArchivoConKey(archivo, key);
            log.info("Archivo subido a S3: {} ({} bytes)", key, archivo.getSize());
            return url;
        }

        // 2. Fallback local (solo desarrollo sin cloud configurado)
        String nombreSeguro = nombreOriginal.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        String uuid = UUID.randomUUID().toString();
        String nombreFinal = uuid + "_" + nombreSeguro;

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path destino = uploadPath.resolve(nombreFinal);
        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        log.info("Archivo subido a LOCAL: {} ({} bytes)", nombreFinal, archivo.getSize());

        return "/api/v1/archivos/" + nombreFinal;
    }

    /**
     * Sube un archivo de formulario a S3 y registra el documento en la colección documentos_tramite.
     *
     * @param archivo            archivo multipart a subir
     * @param empresaId          ID de la empresa
     * @param clienteId          ID del cliente dueño del trámite
     * @param tramiteId          ID del trámite
     * @param nodoId             ID del nodo del formulario
     * @param departamentoNombre nombre del departamento activo en el nodo
     * @param campoClave         nombre del campo del formulario al que pertenece el archivo
     * @param subidoPorId        userId de quien sube el archivo
     * @param rol                CLIENTE o FUNCIONARIO
     * @return URL pública del archivo en S3
     */
    public String subirArchivoFormulario(MultipartFile archivo,
                                          String empresaId,
                                          String clienteId,
                                          String tramiteId,
                                          String nodoId,
                                          String departamentoNombre,
                                          String campoClave,
                                          String subidoPorId,
                                          LlenadoPor rol) throws IOException {
        if (s3Service == null) {
            log.warn("S3Service no disponible. Usando subida genérica.");
            return subirArchivo(archivo);
        }

        String url = s3Service.subirArchivo(archivo, empresaId, clienteId, tramiteId, departamentoNombre);

        if (documentoTramiteRepo != null) {
            documentoTramiteRepo.save(DocumentoTramite.builder()
                .empresaId(empresaId)
                .clienteId(clienteId)
                .tramiteId(tramiteId)
                .nodoId(nodoId)
                .departamentoNombre(departamentoNombre)
                .campoClave(campoClave)
                .nombreArchivo(archivo.getOriginalFilename())
                .tipoMime(archivo.getContentType())
                .urlS3(url)
                .s3Key(s3Service.extraerKeyDeUrl(url))
                .tamanioBytes(archivo.getSize())
                .subidoPor(subidoPorId)
                .subidoPorRol(rol)
                .subidoEn(LocalDateTime.now())
                .build());
        }

        return url;
    }

    public Path resolverRuta(String nombreArchivo) {
        return Paths.get(uploadDir).resolve(nombreArchivo);
    }
}
