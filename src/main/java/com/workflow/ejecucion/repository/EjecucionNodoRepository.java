package com.workflow.ejecucion.repository;

import com.workflow.ejecucion.model.EjecucionNodo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EjecucionNodoRepository extends MongoRepository<EjecucionNodo, String> {
    List<EjecucionNodo> findByTramiteId(String tramiteId);
    List<EjecucionNodo> findByDepartamentoIdAndEstado(String departamentoId, String estado);
    List<EjecucionNodo> findByFuncionarioIdAndEstado(String funcionarioId, String estado);
    List<EjecucionNodo> findByNodoIdAndEstadoIn(String nodoId, List<String> estados);
}
