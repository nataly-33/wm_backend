package com.workflow.politica.repository;

import com.workflow.politica.model.VersionHistorial;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VersionHistorialRepository extends MongoRepository<VersionHistorial, String> {
    List<VersionHistorial> findByPoliticaIdOrderByFechaCambioDesc(String politicaId);
}
