package com.workflow.nodo.repository;

import com.workflow.nodo.model.Nodo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NodoRepository extends MongoRepository<Nodo, String> {
    List<Nodo> findByPoliticaIdAndActivoTrue(String politicaId);
    Optional<Nodo> findByIdAndActivoTrue(String id);
}
