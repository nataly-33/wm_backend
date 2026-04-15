package com.workflow.formulario.service;

import com.workflow.formulario.dto.CrearFormularioRequest;
import com.workflow.formulario.dto.FormularioCampoRequest;
import com.workflow.formulario.dto.FormularioResponse;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FormularioService {
    private final FormularioRepository formularioRepository;
    private final NodoRepository nodoRepository;
    private final PoliticaRepository politicaRepository;
    private final UsuarioRepository usuarioRepository;

    private void validarPoliticaEmpresa(String empresaId, String politicaId) {
        politicaRepository.findByIdAndEmpresaIdAndActivoTrue(politicaId, empresaId)
                .orElseThrow(() -> new RuntimeException("Politica no encontrada"));
    }

    private Nodo obtenerNodoActivo(String nodoId) {
        return nodoRepository.findByIdAndActivoTrue(nodoId)
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado"));
    }

    private void validarAdminDepartamento(String userId, String rol, Nodo nodo) {
        if (!"ADMIN_DEPARTAMENTO".equals(rol)) {
            throw new RuntimeException("Solo ADMIN_DEPARTAMENTO puede gestionar formularios");
        }
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (usuario.getDepartamentoId() == null || !usuario.getDepartamentoId().equals(nodo.getDepartamentoId())) {
            throw new RuntimeException("No tienes permiso sobre este formulario");
        }
    }

    private List<Formulario.CampoFormulario> mapCampos(List<FormularioCampoRequest> campos) {
        if (campos == null) {
            return List.of();
        }
        return campos.stream().map(campo -> Formulario.CampoFormulario.builder()
                .nombre(campo.getNombre())
                .etiqueta(campo.getEtiqueta())
                .tipo(campo.getTipo())
                .requerido(campo.getRequerido())
                .esCampoPrioridad(campo.getEsCampoPrioridad())
                .opciones(campo.getOpciones())
                .build()).toList();
    }

    public FormularioResponse crear(String empresaId, String userId, String rol, CrearFormularioRequest request) {
        validarPoliticaEmpresa(empresaId, request.getPoliticaId());
        Nodo nodo = obtenerNodoActivo(request.getNodoId());
        if (!nodo.getPoliticaId().equals(request.getPoliticaId())) {
            throw new RuntimeException("El nodo no pertenece a la politica");
        }
        validarAdminDepartamento(userId, rol, nodo);

        Formulario formulario = Formulario.builder()
                .politicaId(request.getPoliticaId())
                .nodoId(request.getNodoId())
                .nombre(request.getNombre())
                .campos(mapCampos(request.getCampos()))
                .generadoPorIa(Boolean.TRUE.equals(request.getGeneradoPorIa()))
                .creadoPor(userId)
                .activo(true)
                .build();
        Formulario guardado = formularioRepository.save(formulario);
        nodo.setFormularioId(guardado.getId());
        nodoRepository.save(nodo);
        return FormularioResponse.fromEntity(guardado);
    }

    public FormularioResponse obtenerPorNodo(String empresaId, String nodoId) {
        Nodo nodo = obtenerNodoActivo(nodoId);
        validarPoliticaEmpresa(empresaId, nodo.getPoliticaId());
        Formulario formulario = formularioRepository.findByNodoIdAndActivoTrue(nodoId)
                .orElseThrow(() -> new RuntimeException("Formulario no encontrado para el nodo"));
        return FormularioResponse.fromEntity(formulario);
    }

    public FormularioResponse actualizar(String empresaId, String userId, String rol, String formularioId, CrearFormularioRequest request) {
        Formulario formulario = formularioRepository.findByIdAndActivoTrue(formularioId)
                .orElseThrow(() -> new RuntimeException("Formulario no encontrado"));
        validarPoliticaEmpresa(empresaId, formulario.getPoliticaId());
        Nodo nodo = obtenerNodoActivo(formulario.getNodoId());
        validarAdminDepartamento(userId, rol, nodo);

        formulario.setNombre(request.getNombre());
        formulario.setCampos(mapCampos(request.getCampos()));
        formulario.setGeneradoPorIa(Boolean.TRUE.equals(request.getGeneradoPorIa()));
        return FormularioResponse.fromEntity(formularioRepository.save(formulario));
    }

    public void eliminar(String empresaId, String userId, String rol, String formularioId) {
        Formulario formulario = formularioRepository.findByIdAndActivoTrue(formularioId)
                .orElseThrow(() -> new RuntimeException("Formulario no encontrado"));
        validarPoliticaEmpresa(empresaId, formulario.getPoliticaId());
        Nodo nodo = obtenerNodoActivo(formulario.getNodoId());
        validarAdminDepartamento(userId, rol, nodo);

        formulario.setActivo(false);
        formularioRepository.save(formulario);
        if (formularioId.equals(nodo.getFormularioId())) {
            nodo.setFormularioId(null);
            nodoRepository.save(nodo);
        }
    }
}
