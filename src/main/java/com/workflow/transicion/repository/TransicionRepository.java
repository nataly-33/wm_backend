package com.workflow.transicion.repository;

import com.workflow.transicion.model.Transicion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransicionRepository extends MongoRepository<Transicion, String> {
    List<Transicion> findByPoliticaIdAndActivoTrue(String politicaId);
    Optional<Transicion> findByIdAndActivoTrue(String id);
}
