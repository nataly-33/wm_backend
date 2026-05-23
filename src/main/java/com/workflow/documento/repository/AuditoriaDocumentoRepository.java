package com.workflow.documento.repository;

import com.workflow.documento.model.AuditoriaDocumento;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AuditoriaDocumentoRepository extends MongoRepository<AuditoriaDocumento, String> {
    List<AuditoriaDocumento> findByDocumentoIdOrderByFechaHoraDesc(String documentoId);
}
