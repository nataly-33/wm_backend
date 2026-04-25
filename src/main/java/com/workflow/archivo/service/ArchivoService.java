package com.workflow.archivo.service;

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

    public String subirArchivo(MultipartFile archivo) throws IOException {
        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            nombreOriginal = "archivo";
        }

        String nombreSeguro = nombreOriginal.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        String uuid = UUID.randomUUID().toString();
        String nombreFinal = uuid + "_" + nombreSeguro;

        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path destino = uploadPath.resolve(nombreFinal);
        Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

        log.info("Archivo subido: {} ({} bytes)", nombreFinal, archivo.getSize());

        return "/api/v1/archivos/" + nombreFinal;
    }

    public Path resolverRuta(String nombreArchivo) {
        return Paths.get(uploadDir).resolve(nombreArchivo);
    }
}
