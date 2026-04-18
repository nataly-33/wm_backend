package com.workflow.departamento.repository;

import com.workflow.departamento.model.Departamento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartamentoRepository extends MongoRepository<Departamento, String> {
    List<Departamento> findByEmpresaId(String empresaId);
    List<Departamento> findByEmpresaIdAndActivo(String empresaId, Boolean activo);
    List<Departamento> findByEmpresaIdAndAdminDepartamentoIdIsNullAndActivoTrue(String empresaId);
    List<Departamento> findByEmpresaIdAndAdminDepartamentoIdIsNotNullAndActivoTrue(String empresaId);
    Optional<Departamento> findByIdAndEmpresaId(String id, String empresaId);
}
