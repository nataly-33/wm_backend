package com.workflow.documento.repository;

import com.workflow.documento.model.DocumentoTramite;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoTramiteRepository extends MongoRepository<DocumentoTramite, String> {
    List<DocumentoTramite> findByTramiteIdOrderBySubidoEnAsc(String tramiteId);
    List<DocumentoTramite> findByTramiteIdAndDepartamentoNombre(String tramiteId, String departamentoNombre);
    List<DocumentoTramite> findByClienteId(String clienteId);
    /** Buscar por URL pública de S3 (campo urlS3) — evita findAll().stream() */
    Optional<DocumentoTramite> findByUrlS3(String urlS3);
}
