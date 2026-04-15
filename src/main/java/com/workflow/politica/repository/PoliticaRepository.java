package com.workflow.politica.repository;

import com.workflow.politica.model.Politica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoliticaRepository extends MongoRepository<Politica, String> {
    List<Politica> findByEmpresaIdAndActivoTrue(String empresaId);
    Optional<Politica> findByIdAndEmpresaIdAndActivoTrue(String id, String empresaId);
}
