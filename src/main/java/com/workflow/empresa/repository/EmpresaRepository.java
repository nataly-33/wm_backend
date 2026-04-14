package com.workflow.empresa.repository;

import com.workflow.empresa.model.Empresa;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmpresaRepository extends MongoRepository<Empresa, String> {
    List<Empresa> findAllByActivoTrue();
}
