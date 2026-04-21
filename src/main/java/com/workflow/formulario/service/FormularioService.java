package com.workflow.formulario.service;

import com.workflow.formulario.dto.CrearFormularioRequest;
import com.workflow.formulario.dto.FormularioCampoRequest;
import com.workflow.formulario.dto.FormularioResponse;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private void validarGestionFormulario(String userId, String rol, Nodo nodo) {
        if ("ADMIN_GENERAL".equals(rol)) {
            return;
        }
        if (!"ADMIN_DEPARTAMENTO".equals(rol)) {
            throw new RuntimeException("Solo ADMIN_DEPARTAMENTO o ADMIN_GENERAL pueden gestionar formularios");
        }
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (usuario.getDepartamentoId() == null || !usuario.getDepartamentoId().equals(nodo.getDepartamentoId())) {
            throw new RuntimeException("No tienes permiso sobre este formulario");
        }
    }

    private void validarAccesoDepartamento(String userId, String rol, String departamentoId) {
        if ("ADMIN_GENERAL".equals(rol)) {
            return;
        }
        if (!"ADMIN_DEPARTAMENTO".equals(rol)) {
            throw new RuntimeException("Sin permisos para consultar formularios del departamento");
        }
        Usuario usuario = usuarioRepository.findByIdAndActivoTrue(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (!Objects.equals(usuario.getDepartamentoId(), departamentoId)) {
            throw new RuntimeException("Solo puedes consultar formularios de tu departamento");
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
        validarGestionFormulario(userId, rol, nodo);

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
        validarGestionFormulario(userId, rol, nodo);

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
        validarGestionFormulario(userId, rol, nodo);

        formulario.setActivo(false);
        formularioRepository.save(formulario);
        if (formularioId.equals(nodo.getFormularioId())) {
            nodo.setFormularioId(null);
            nodoRepository.save(nodo);
        }
    }

    public List<Map<String, Object>> listarPorEmpresa(String empresaId, String rol) {
        if (!"ADMIN_GENERAL".equals(rol)) {
            throw new RuntimeException("Solo ADMIN_GENERAL puede ver todos los formularios de la empresa");
        }

        List<Politica> politicas = politicaRepository.findByEmpresaIdAndActivoTrue(empresaId);
        if (politicas.isEmpty()) {
            return List.of();
        }

        List<String> politicaIds = politicas.stream().map(Politica::getId).collect(Collectors.toList());
        Map<String, Politica> politicaPorId = politicas.stream()
                .collect(Collectors.toMap(Politica::getId, p -> p));

        List<Formulario> formularios = formularioRepository.findByPoliticaIdInAndActivoTrue(politicaIds);
        List<String> nodoIds = formularios.stream().map(Formulario::getNodoId).distinct().collect(Collectors.toList());
        Map<String, Nodo> nodoPorId = nodoRepository.findAllById(nodoIds).stream()
                .collect(Collectors.toMap(Nodo::getId, n -> n));

        Map<String, List<Map<String, Object>>> agrupado = new LinkedHashMap<>();
        for (Formulario f : formularios) {
            Politica politica = politicaPorId.get(f.getPoliticaId());
            Nodo nodo = nodoPorId.get(f.getNodoId());
            if (politica == null || nodo == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("formulario", FormularioResponse.fromEntity(f));
            item.put("nodoId", nodo.getId());
            item.put("nodoNombre", nodo.getNombre());
            item.put("departamentoId", nodo.getDepartamentoId());

            agrupado.computeIfAbsent(politica.getId(), key -> new ArrayList<>()).add(item);
        }

        List<Map<String, Object>> respuesta = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : agrupado.entrySet()) {
            Politica politica = politicaPorId.get(entry.getKey());
            if (politica == null) {
                continue;
            }
            Map<String, Object> bloque = new LinkedHashMap<>();
            bloque.put("politicaId", politica.getId());
            bloque.put("politicaNombre", politica.getNombre());
            bloque.put("formularios", entry.getValue());
            respuesta.add(bloque);
        }
        return respuesta;
    }

    public List<Map<String, Object>> listarPorDepartamento(String userId, String rol, String departamentoId) {
        validarAccesoDepartamento(userId, rol, departamentoId);

        List<Nodo> nodos = nodoRepository.findByDepartamentoIdAndActivoTrue(departamentoId).stream()
                .filter(n -> "TAREA".equals(n.getTipo()) || "DECISION".equals(n.getTipo()))
                .collect(Collectors.toList());

        if (nodos.isEmpty()) {
            return List.of();
        }

        List<String> nodoIds = nodos.stream().map(Nodo::getId).collect(Collectors.toList());
        List<Formulario> formularios = formularioRepository.findByNodoIdInAndActivoTrue(nodoIds);

        List<String> politicaIds = nodos.stream().map(Nodo::getPoliticaId).distinct().collect(Collectors.toList());
        Map<String, Politica> politicaPorId = politicaRepository.findAllById(politicaIds).stream()
                .collect(Collectors.toMap(Politica::getId, p -> p));

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Nodo nodo : nodos) {
            Formulario formulario = formularios.stream()
                    .filter(f -> f.getNodoId().equals(nodo.getId()))
                    .findFirst()
                    .orElse(null);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodoId", nodo.getId());
            item.put("nodoNombre", nodo.getNombre());
            item.put("politicaId", nodo.getPoliticaId());
            item.put("politicaNombre", politicaPorId.containsKey(nodo.getPoliticaId())
                    ? politicaPorId.get(nodo.getPoliticaId()).getNombre()
                    : "");
            item.put("formulario", formulario == null ? null : FormularioResponse.fromEntity(formulario));
            resultado.add(item);
        }

        return resultado;
    }
}
