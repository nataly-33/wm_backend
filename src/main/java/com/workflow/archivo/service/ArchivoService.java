package com.workflow.archivo.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class ArchivoService {

    @Value("${archivos.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${azure.storage.connection-string:}")
    private String azureConnectionString;

    @Value("${azure.storage.container-name:archivos}")
    private String containerName;

    private BlobContainerClient blobContainerClient;

    @PostConstruct
    public void init() {
        if (azureConnectionString != null && !azureConnectionString.isBlank()) {
            try {
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                        .connectionString(azureConnectionString)
                        .buildClient();
                this.blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(containerName);
                log.info("Azure Blob Storage configurado correctamente en el contenedor: {}", containerName);
            } catch (Exception e) {
                log.error("Error al conectar con Azure Blob Storage: {}", e.getMessage());
            }
        } else {
            log.info("Azure Storage no configurado, se usara almacenamiento local en: {}", uploadDir);
        }
    }

    public String subirArchivo(MultipartFile archivo) throws IOException {
        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            nombreOriginal = "archivo";
        }

        String nombreSeguro = nombreOriginal.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        String uuid = UUID.randomUUID().toString();
        String nombreFinal = uuid + "_" + nombreSeguro;

        // Si Azure esta configurado, subir alli
        if (blobContainerClient != null) {
            BlobClient blobClient = blobContainerClient.getBlobClient(nombreFinal);
            blobClient.upload(archivo.getInputStream(), archivo.getSize(), true);
            log.info("Archivo subido a AZURE: {} ({} bytes)", nombreFinal, archivo.getSize());
            return blobClient.getBlobUrl(); // Devuelve la URL publica de Azure
        }

        // Si no, usar local (Desarrollo)
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path destino = uploadPath.resolve(nombreFinal);
        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        log.info("Archivo subido a LOCAL: {} ({} bytes)", nombreFinal, archivo.getSize());

        return "/api/v1/archivos/" + nombreFinal;
    }

    public Path resolverRuta(String nombreArchivo) {
        return Paths.get(uploadDir).resolve(nombreArchivo);
    }
}
