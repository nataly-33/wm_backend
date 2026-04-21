package com.workflow.usuario.repository;

import com.workflow.usuario.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByEmailAndActivoTrue(String email);

    List<Usuario> findByEmpresaIdAndActivoTrue(String empresaId);
    List<Usuario> findByEmpresaIdAndRolAndActivoTrue(String empresaId, String rol);
    List<Usuario> findByDepartamentoIdAndActivoTrue(String departamentoId);

    Optional<Usuario> findByIdAndActivoTrue(String id);

    boolean existsByEmail(String email);

    long countByDepartamentoIdAndActivoTrue(String departamentoId);
}
