package com.workflow.formulario.repository;

import com.workflow.formulario.model.Formulario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormularioRepository extends MongoRepository<Formulario, String> {
    Optional<Formulario> findByNodoIdAndActivoTrue(String nodoId);
    Optional<Formulario> findByIdAndActivoTrue(String id);
}
