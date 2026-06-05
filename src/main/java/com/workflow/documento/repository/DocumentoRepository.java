package com.workflow.documento.repository;

import com.workflow.documento.model.Documento;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends MongoRepository<Documento, String> {
    List<Documento> findByEmpresaIdAndEliminadoFalse(String empresaId);
    List<Documento> findByEmpresaIdAndTramiteIdAndEliminadoFalse(String empresaId, String tramiteId);
    List<Documento> findByEmpresaIdAndPoliticaIdAndEliminadoFalse(String empresaId, String politicaId);
    List<Documento> findByEmpresaIdAndCarpetaIdAndEliminadoFalse(String empresaId, String carpetaId);
    Optional<Documento> findByUrlArchivoAndEliminadoFalse(String urlArchivo);
    List<Documento> findByEsDocumentoOficinaAndEliminadoFalse(boolean esDocumentoOficina);
}
