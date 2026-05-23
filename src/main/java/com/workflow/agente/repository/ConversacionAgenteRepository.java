package com.workflow.agente.repository;

import com.workflow.agente.model.ConversacionAgente;
import com.workflow.agente.model.EstadoConversacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversacionAgenteRepository extends MongoRepository<ConversacionAgente, String> {
    List<ConversacionAgente> findByClienteId(String clienteId);
    Optional<ConversacionAgente> findByClienteIdAndEstadoNot(String clienteId, EstadoConversacion estado);
}
