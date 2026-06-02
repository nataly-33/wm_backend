package com.workflow.agente.repository;

import com.workflow.agente.model.ConversacionAgente;
import com.workflow.agente.model.EstadoConversacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversacionAgenteRepository extends MongoRepository<ConversacionAgente, String> {
    List<ConversacionAgente> findByClienteId(String clienteId);

    Optional<ConversacionAgente> findFirstByClienteIdAndEstadoNotOrderByUltimaActividadEnDesc(
            String clienteId, EstadoConversacion estado);

    List<ConversacionAgente> findByClienteIdAndEstadoNot(String clienteId, EstadoConversacion estado);

    void deleteByClienteIdAndEstadoNot(String clienteId, EstadoConversacion estado);

    Optional<ConversacionAgente> findByTramiteId(String tramiteId);
}
