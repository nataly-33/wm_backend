package com.workflow.ejecucion.repository;

import com.workflow.ejecucion.model.EjecucionNodo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EjecucionNodoRepository extends MongoRepository<EjecucionNodo, String> {
    List<EjecucionNodo> findByTramiteId(String tramiteId);
    List<EjecucionNodo> findByTramiteIdIn(List<String> tramiteIds);
    List<EjecucionNodo> findByTramiteIdOrderByIniciadoEnDesc(String tramiteId);
    List<EjecucionNodo> findByDepartamentoId(String departamentoId);
    List<EjecucionNodo> findByDepartamentoIdAndEstado(String departamentoId, String estado);
    List<EjecucionNodo> findByDepartamentoIdAndEstadoIn(String departamentoId, List<String> estados);
    List<EjecucionNodo> findByFuncionarioIdAndEstado(String funcionarioId, String estado);
    List<EjecucionNodo> findByFuncionarioIdAndEstadoIn(String funcionarioId, List<String> estados);
    long countByFuncionarioIdAndEstadoIn(String funcionarioId, List<String> estados);
    List<EjecucionNodo> findByNodoIdAndEstadoIn(String nodoId, List<String> estados);
    List<EjecucionNodo> findByNodoId(String nodoId);
}
