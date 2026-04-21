package com.workflow.tramite.repository;

import com.workflow.tramite.model.Tramite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TramiteRepository extends MongoRepository<Tramite, String> {
    List<Tramite> findByIdIn(List<String> ids);
    List<Tramite> findByEmpresaId(String empresaId);
    List<Tramite> findByPoliticaId(String politicaId);
    List<Tramite> findByPoliticaIdAndEstadoGeneralIn(String politicaId, List<String> estados);
    // Para ver los iniciados por un admin o funcionario
    List<Tramite> findByIniciadoPor(String usuarioId);
}
