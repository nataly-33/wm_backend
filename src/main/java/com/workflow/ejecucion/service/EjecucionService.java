package com.workflow.ejecucion.service;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.ejecucion.dto.CampoRellenadoDto;
import com.workflow.ejecucion.dto.EjecucionDetalladaResponse;
import com.workflow.ejecucion.dto.FormularioRellenadoResponse;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.tramite.service.MotorWorkflowService;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EjecucionService {
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final MotorWorkflowService motorWorkflowService;
    private final NodoRepository nodoRepository;
    private final TramiteRepository tramiteRepository;
    private final PoliticaRepository politicaRepository;
    private final DepartamentoRepository departamentoRepository;
    private final FormularioRepository formularioRepository;
    private final UsuarioRepository usuarioRepository;

    public List<EjecucionNodo> listarPorDepartamento(String departamentoId) {
        return ejecucionNodoRepository.findByDepartamentoIdAndEstadoIn(
                departamentoId,
                List.of("PENDIENTE", "EN_PROCESO", "PENDIENTE_SIN_ASIGNAR")
        );
    }

    public List<EjecucionNodo> listarPorFuncionario(String funcionarioId) {
        return ejecucionNodoRepository.findByFuncionarioIdAndEstadoIn(
                funcionarioId,
                List.of("PENDIENTE", "EN_PROCESO")
        );
    }

    public List<EjecucionDetalladaResponse> listarPorFuncionarioDetallado(String funcionarioId) {
        List<EjecucionNodo> ejecuciones = ejecucionNodoRepository.findByFuncionarioIdAndEstadoIn(
                funcionarioId,
                List.of("PENDIENTE", "EN_PROCESO")
        );

        List<EjecucionDetalladaResponse> resultado = ejecuciones.stream()
                .map(this::enriquecerEjecucion)
                .collect(Collectors.toList());

        // Ordenar: ALTA primero, luego MEDIA, luego BAJA; dentro de cada prioridad por fechaLimite ASC
        resultado.sort(Comparator
                .comparingInt((EjecucionDetalladaResponse e) -> prioridadOrden(e.getPrioridad()))
                .thenComparing(Comparator.comparing(
                        EjecucionDetalladaResponse::getFechaLimite,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
        );

        return resultado;
    }

    private int prioridadOrden(String prioridad) {
        if (prioridad == null) return 2;
        return switch (prioridad) {
            case "ALTA" -> 0;
            case "MEDIA" -> 1;
            case "BAJA" -> 2;
            default -> 2;
        };
    }

    private EjecucionDetalladaResponse enriquecerEjecucion(EjecucionNodo ejecucion) {
        // Buscar nodo
        String nombreNodo = ejecucion.getNodoId();
        String departamentoId = ejecucion.getDepartamentoId();
        String politicaId = null;

        Optional<Nodo> nodoOpt = nodoRepository.findById(ejecucion.getNodoId());
        if (nodoOpt.isPresent()) {
            Nodo nodo = nodoOpt.get();
            nombreNodo = nodo.getNombre();
            if (nodo.getDepartamentoId() != null) {
                departamentoId = nodo.getDepartamentoId();
            }
            politicaId = nodo.getPoliticaId();
        }

        // Buscar trámite
        String tituloTramite = ejecucion.getTramiteId();
        String prioridad = "MEDIA";
        LocalDateTime fechaLimite = null;

        Optional<Tramite> tramiteOpt = tramiteRepository.findById(ejecucion.getTramiteId());
        if (tramiteOpt.isPresent()) {
            Tramite tramite = tramiteOpt.get();
            tituloTramite = tramite.getTitulo();
            prioridad = tramite.getPrioridad() != null ? tramite.getPrioridad() : "MEDIA";
            fechaLimite = tramite.getFechaLimite();
            if (politicaId == null) {
                politicaId = tramite.getPoliticaId();
            }
        }

        // Buscar política
        String nombrePolitica = politicaId;
        if (politicaId != null) {
            Optional<Politica> politicaOpt = politicaRepository.findById(politicaId);
            if (politicaOpt.isPresent()) {
                nombrePolitica = politicaOpt.get().getNombre();
            }
        }

        // Buscar departamento
        String nombreDepartamento = departamentoId;
        if (departamentoId != null) {
            Optional<Departamento> deptoOpt = departamentoRepository.findById(departamentoId);
            if (deptoOpt.isPresent()) {
                nombreDepartamento = deptoOpt.get().getNombre();
            }
        }

        return EjecucionDetalladaResponse.builder()
                .id(ejecucion.getId())
                .estado(ejecucion.getEstado())
                .nombreNodo(nombreNodo)
                .nombrePolitica(nombrePolitica)
                .tituloTramite(tituloTramite)
                .prioridad(prioridad)
                .fechaLimite(fechaLimite)
                .iniciadoEn(ejecucion.getIniciadoEn())
                .nombreDepartamento(nombreDepartamento)
                .tramiteId(ejecucion.getTramiteId())
                .politicaId(politicaId)
                .nodoId(ejecucion.getNodoId())
                .build();
    }

    public List<EjecucionNodo> listarPorTramite(String tramiteId) {
        return ejecucionNodoRepository.findByTramiteIdOrderByIniciadoEnDesc(tramiteId);
    }

    public EjecucionNodo iniciar(String id, String userId) {
        EjecucionNodo ejecucion = obtener(id);
        if (!"PENDIENTE".equals(ejecucion.getEstado())) {
            if ("EN_PROCESO".equals(ejecucion.getEstado())) {
                return ejecucion;
            }
            throw new RuntimeException("Solo se pueden iniciar ejecuciones en estado PENDIENTE");
        }

        if (ejecucion.getFuncionarioId() != null && userId != null && !ejecucion.getFuncionarioId().equals(userId)) {
            throw new RuntimeException("Esta tarea está asignada a otro funcionario");
        }

        if (ejecucion.getFuncionarioId() == null && userId != null && !userId.isBlank()) {
            ejecucion.setFuncionarioId(userId);
        }

        ejecucion.setEstado("EN_PROCESO");
        if (ejecucion.getIniciadoEn() == null) {
            ejecucion.setIniciadoEn(LocalDateTime.now());
        }
        return ejecucionNodoRepository.save(ejecucion);
    }

    public void completar(String id, Map<String, Object> respuesta) {
        motorWorkflowService.completarEjecucion(id, respuesta);
    }

    public void rechazar(String id, String observaciones) {
        motorWorkflowService.rechazarEjecucion(id, observaciones);
    }

    public EjecucionNodo obtener(String id) {
        return ejecucionNodoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Ejecución no encontrada"));
    }

    public List<FormularioRellenadoResponse> obtenerHistorialFormularios(String tramiteId) {
        List<EjecucionNodo> ejecuciones = ejecucionNodoRepository.findByTramiteIdOrderByIniciadoEnDesc(tramiteId);

        // Ordenar cronologicamente (mas antigua primero)
        List<EjecucionNodo> ordenadas = ejecuciones.stream()
                .filter(e -> "COMPLETADO".equals(e.getEstado()))
                .sorted(Comparator.comparing(
                        EjecucionNodo::getCompletadoEn,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .collect(Collectors.toList());

        List<FormularioRellenadoResponse> resultado = new ArrayList<>();

        for (EjecucionNodo ejecucion : ordenadas) {
            String nombreNodo = ejecucion.getNodoId();
            String deptoNombre = null;
            String funcionarioNombre = null;

            // Buscar nodo
            Optional<Nodo> nodoOpt = nodoRepository.findById(ejecucion.getNodoId());
            if (nodoOpt.isPresent()) {
                Nodo nodo = nodoOpt.get();
                nombreNodo = nodo.getNombre();
                if (nodo.getDepartamentoId() != null) {
                    deptoNombre = departamentoRepository.findById(nodo.getDepartamentoId())
                            .map(Departamento::getNombre).orElse(nodo.getDepartamentoId());
                }
            }

            // Buscar funcionario
            if (ejecucion.getFuncionarioId() != null) {
                Optional<Usuario> usuarioOpt = usuarioRepository.findById(ejecucion.getFuncionarioId());
                if (usuarioOpt.isPresent()) {
                    funcionarioNombre = usuarioOpt.get().getNombre();
                }
            }

            // Mapear campos del formulario
            List<CampoRellenadoDto> camposRellenados = new ArrayList<>();
            Optional<Formulario> formOpt = formularioRepository.findByNodoIdAndActivoTrue(ejecucion.getNodoId());
            if (formOpt.isPresent() && ejecucion.getRespuestaFormulario() != null) {
                Formulario formulario = formOpt.get();
                Map<String, Object> respuesta = ejecucion.getRespuestaFormulario();
                if (formulario.getCampos() != null) {
                    for (Formulario.CampoFormulario campo : formulario.getCampos()) {
                        Object valor = respuesta.get(campo.getNombre());
                        boolean esArchivo = "ARCHIVO".equals(campo.getTipo()) || "IMAGEN".equals(campo.getTipo());
                        camposRellenados.add(CampoRellenadoDto.builder()
                                .nombre(campo.getNombre())
                                .etiqueta(campo.getEtiqueta() != null ? campo.getEtiqueta() : campo.getNombre())
                                .tipo(campo.getTipo())
                                .valor(valor)
                                .esArchivo(esArchivo)
                                .build());
                    }
                }
            } else if (ejecucion.getRespuestaFormulario() != null) {
                // Sin formulario definido — mostrar campos directamente del mapa
                for (Map.Entry<String, Object> entry : ejecucion.getRespuestaFormulario().entrySet()) {
                    camposRellenados.add(CampoRellenadoDto.builder()
                            .nombre(entry.getKey())
                            .etiqueta(entry.getKey())
                            .tipo("TEXTO")
                            .valor(entry.getValue())
                            .esArchivo(false)
                            .build());
                }
            }

            resultado.add(FormularioRellenadoResponse.builder()
                    .ejecucionId(ejecucion.getId())
                    .nodoId(ejecucion.getNodoId())
                    .nombreNodo(nombreNodo)
                    .departamentoNombre(deptoNombre)
                    .funcionarioNombre(funcionarioNombre)
                    .estado(ejecucion.getEstado())
                    .completadoEn(ejecucion.getCompletadoEn())
                    .observaciones(ejecucion.getObservaciones())
                    .campos(camposRellenados)
                    .build());
        }

        return resultado;
    }

    public EjecucionNodo reasignar(String id, String funcionarioId) {
        EjecucionNodo ejecucion = obtener(id);
        if ("COMPLETADO".equals(ejecucion.getEstado()) || "RECHAZADO".equals(ejecucion.getEstado())) {
            throw new RuntimeException("No se puede reasignar una ejecución ya finalizada");
        }

        ejecucion.setFuncionarioId(funcionarioId);
        if ("PENDIENTE_SIN_ASIGNAR".equals(ejecucion.getEstado())) {
            ejecucion.setEstado("PENDIENTE");
        }
        return ejecucionNodoRepository.save(ejecucion);
    }
}
