package com.workflow.agente.service;

import com.workflow.agente.dto.EstadoTramiteClienteResponse;
import com.workflow.agente.model.ConversacionAgente;
import com.workflow.agente.model.EstadoConversacion;
import com.workflow.agente.model.MensajeChat;
import com.workflow.agente.repository.ConversacionAgenteRepository;
import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.notificacion.service.NotificacionService;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.tramite.service.MotorWorkflowService;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgenteService {

    private final ConversacionAgenteRepository conversacionRepository;
    private final PoliticaRepository politicaRepository;
    private final TramiteRepository tramiteRepository;
    private final NodoRepository nodoRepository;
    private final FormularioRepository formularioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;
    private final MotorWorkflowService motorWorkflowService;
    private final RestTemplate restTemplate;

    @Value("${ia.service.url:http://localhost:8001}")
    private String iaServiceUrl;

    // ─── Obtener conversacion activa eliminando duplicados ────────────────────

    private ConversacionAgente obtenerConversacionActiva(String clienteId) {
        List<ConversacionAgente> activas = conversacionRepository
                .findByClienteIdAndEstadoNot(clienteId, EstadoConversacion.COMPLETADO)
                .stream()
                .filter(c -> c.getEstado() != EstadoConversacion.RECHAZADO)
                .sorted(Comparator.comparing(ConversacionAgente::getUltimaActividadEn,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (activas.isEmpty()) return null;

        if (activas.size() > 1) {
            List<ConversacionAgente> duplicados = activas.subList(1, activas.size());
            conversacionRepository.deleteAll(duplicados);
            log.warn("Se eliminaron {} conversaciones duplicadas para cliente {}", duplicados.size(), clienteId);
        }

        return activas.get(0);
    }

    // ─── Procesar mensaje del cliente ─────────────────────────────────────────

    public Map<String, Object> procesarMensaje(String conversacionId, String clienteId, String mensaje, String tipo) {
        ConversacionAgente conversacion;

        if (conversacionId != null && !conversacionId.isBlank()) {
            conversacion = conversacionRepository.findById(conversacionId)
                    .orElse(null);
        } else {
            conversacion = obtenerConversacionActiva(clienteId);
        }

        if (conversacion == null) {
            conversacion = crearNuevaConversacion(clienteId);
        }

        agregarMensaje(conversacion, "cliente", mensaje, tipo != null ? tipo : "texto");

        Map<String, Object> respuesta;
        switch (conversacion.getEstado()) {
            case DETECTANDO_POLITICA    -> respuesta = manejarDeteccionPolitica(conversacion, mensaje, clienteId);
            case CONFIRMANDO_POLITICA   -> respuesta = manejarConfirmacionDeterministica(conversacion, mensaje);
            case CONFIRMACION_FINAL     -> respuesta = manejarConfirmacionFinal(conversacion, mensaje);
            case RECOPILANDO_DATOS_NODO -> respuesta = manejarRespuestaCampo(conversacion, mensaje);
            case ESPERANDO_ARCHIVOS -> respuesta = Map.of(
                    "mensajeAgente", "Por favor sube el archivo requerido usando el boton de adjuntar.",
                    "estado", conversacion.getEstado().name()
            );
            case ESPERANDO_APROBACION, TRAMITE_EN_PROCESO -> {
                String msgEstado = obtenerMensajeEstadoActual(conversacion);
                respuesta = Map.of("mensajeAgente", msgEstado, "estado", conversacion.getEstado().name());
            }
            case COMPLETADO -> respuesta = Map.of(
                    "mensajeAgente", "Tu tramite ha sido completado. Puedes ver el resumen en tu historial.",
                    "estado", "COMPLETADO"
            );
            case RECHAZADO -> respuesta = Map.of(
                    "mensajeAgente", "Tu solicitud fue rechazada. Si tienes dudas contacta a CRE.",
                    "estado", "RECHAZADO"
            );
            default -> respuesta = manejarDeteccionPolitica(conversacion, mensaje, clienteId);
        }

        return guardarYRetornar(conversacion, respuesta);
    }

    // ─── Deteccion de politica ────────────────────────────────────────────────

    private Map<String, Object> manejarDeteccionPolitica(ConversacionAgente conv, String mensaje, String clienteId) {
        List<Politica> politicas = politicaRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()) && "ACTIVA".equals(p.getEstado()))
                .collect(Collectors.toList());

        if (politicas.isEmpty()) {
            agregarMensaje(conv, "agente",
                    "Lo siento, no hay tramites disponibles en este momento. Contacta a CRE para mas informacion.",
                    "texto");
            return Map.of("mensajeAgente", "No hay tramites disponibles.", "estado", conv.getEstado().name());
        }

        try {
            List<Map<String, Object>> politicasDto = politicas.stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", p.getId());
                m.put("nombre", p.getNombre());
                m.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "");
                m.put("etiquetas", new ArrayList<>());
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> iaRequest = Map.of("mensaje", mensaje, "politicas", politicasDto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(iaRequest, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> iaResponse = restTemplate.postForObject(
                    iaServiceUrl + "/ia/agente/detectar-politica", entity, Map.class);

            if (iaResponse == null) {
                return respuestaError(conv, "No pude procesar tu solicitud. Intenta de nuevo.");
            }

            String mensajeCliente = (String) iaResponse.get("mensaje_para_cliente");
            @SuppressWarnings("unchecked")
            Map<String, Object> politicaSugerida = (Map<String, Object>) iaResponse.get("politica_sugerida");

            if (politicaSugerida == null) {
                String politicaIdRaw = iaResponse.get("politica_id") != null
                        ? iaResponse.get("politica_id").toString() : null;
                if (politicaIdRaw != null && !politicaIdRaw.isBlank() && !"null".equals(politicaIdRaw)) {
                    String finalPoliticaId = politicaIdRaw;
                    politicaSugerida = politicas.stream()
                            .filter(p -> finalPoliticaId.equals(p.getId()))
                            .map(p -> {
                                Map<String, Object> m = new HashMap<>();
                                m.put("id", p.getId());
                                m.put("nombre", p.getNombre());
                                return m;
                            })
                            .findFirst().orElse(null);
                }
            }

            if (politicaSugerida == null) {
                String nombreDetectado = iaResponse.get("politica_detectada") != null
                        ? iaResponse.get("politica_detectada").toString() : null;
                if (nombreDetectado != null && !nombreDetectado.isBlank() && !"null".equals(nombreDetectado)) {
                    String nombreLower = nombreDetectado.toLowerCase();
                    politicaSugerida = politicas.stream()
                            .filter(p -> p.getNombre() != null && p.getNombre().toLowerCase().contains(
                                    nombreLower.length() > 6 ? nombreLower.substring(0, 6) : nombreLower))
                            .map(p -> {
                                Map<String, Object> m = new HashMap<>();
                                m.put("id", p.getId());
                                m.put("nombre", p.getNombre());
                                return m;
                            })
                            .findFirst().orElse(null);
                }
            }

            if (politicaSugerida == null) {
                agregarMensaje(conv, "agente", mensajeCliente != null ? mensajeCliente
                        : "No entendi bien tu solicitud. Puedes decirme que tramite necesitas?", "texto");
                return Map.of("mensajeAgente", mensajeCliente != null ? mensajeCliente
                        : "No entendi bien tu solicitud.", "estado", conv.getEstado().name());
            }

            String politicaId = (String) politicaSugerida.get("id");
            String nombrePolitica = (String) politicaSugerida.get("nombre");

            Politica politica = politicaRepository.findById(politicaId).orElse(null);
            String empresaId = politica != null ? politica.getEmpresaId() : null;

            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.put("politicaIdPropuesta", politicaId);
            datos.put("politicaNombrePropuesta", nombrePolitica);
            conv.setDatosRecopilados(datos);
            conv.setPoliticaId(politicaId);
            conv.setEmpresaId(empresaId);
            conv.setEstado(EstadoConversacion.CONFIRMANDO_POLITICA);

            String msg = mensajeCliente != null ? mensajeCliente
                    : "Detecte que necesitas: " + nombrePolitica + ". Es correcto?";
            agregarMensaje(conv, "agente", msg, "confirmacion");

            return Map.of("mensajeAgente", msg, "estado", "CONFIRMANDO_POLITICA",
                    "politicaDetectada", nombrePolitica);

        } catch (Exception e) {
            log.warn("Error llamando a IA para detectar politica: {}", e.getMessage());
            String lista = politicas.stream()
                    .map(p -> "- " + p.getNombre())
                    .collect(Collectors.joining("\n"));
            String msg = "Hola! Soy el asistente de CRE. Puedo ayudarte con los siguientes tramites:\n" + lista
                    + "\n\nPor favor dime cual necesitas.";
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", conv.getEstado().name());
        }
    }

    // ─── Confirmacion deterministica (sin LLM) ────────────────────────────────

    private Map<String, Object> manejarConfirmacionDeterministica(ConversacionAgente conv, String mensaje) {
        String msgLower = mensaje.toLowerCase().trim();

        boolean confirmo = msgLower.matches(
                ".*(s[ií]|dale|ok|okey|correcto|exacto|afirmativo|" +
                "confirmo|adelante|procede|as[ií]|claro|bueno|perfecto|listo|" +
                "quiero|quiero ese|ese mismo|ese es).*"
        );

        boolean nego = msgLower.matches(
                ".*(^no$|^no,|no es|incorrecto|otro|diferente|equivocado|" +
                "no quiero|ese no|cambiar|otro tramite).*"
        );

        if (nego) confirmo = false;

        if (confirmo) {
            String politicaId = conv.getPoliticaId();
            if (politicaId == null || politicaId.isBlank()) {
                Map<String, Object> datos = conv.getDatosRecopilados() != null
                        ? conv.getDatosRecopilados() : new HashMap<>();
                politicaId = (String) datos.get("politicaIdPropuesta");
            }
            if (politicaId == null || politicaId.isBlank()) {
                conv.setEstado(EstadoConversacion.DETECTANDO_POLITICA);
                String msg = "No encontre una politica seleccionada. Por favor dime que tramite necesitas.";
                agregarMensaje(conv, "agente", msg, "texto");
                return Map.of("mensajeAgente", msg, "estado", "DETECTANDO_POLITICA");
            }

            Politica politica = politicaRepository.findById(politicaId).orElse(null);
            String nombrePolitica = politica != null ? politica.getNombre() : "el tramite";

            String nombreCliente = usuarioRepository.findByEmailAndActivoTrue(conv.getClienteId())
                    .map(u -> u.getNombre() != null ? u.getNombre().split(" ")[0] : "Cliente")
                    .orElse("Cliente");

            long numTramite = tramiteRepository.countByPoliticaId(politicaId) + 1;
            String nombreTramite = nombreCliente + "_" + nombrePolitica;

            conv.setPoliticaId(politicaId);
            if (politica != null) {
                conv.setEmpresaId(politica.getEmpresaId());
            }
            conv.setEstado(EstadoConversacion.CONFIRMACION_FINAL);
            conv.setNombreTramitePropuesto(nombreTramite);
            conversacionRepository.save(conv);

            String msgConfirmacion = String.format(
                    "Estas seguro de iniciar el tramite #%d - %s?\n\nResponde si para confirmar o no para cancelar.",
                    numTramite, nombreTramite
            );
            agregarMensaje(conv, "agente", msgConfirmacion, "confirmacion");
            return Map.of("mensajeAgente", msgConfirmacion, "estado", "CONFIRMACION_FINAL");
        }

        if (nego) {
            conv.setEstado(EstadoConversacion.DETECTANDO_POLITICA);
            conv.setPoliticaId(null);
            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.remove("politicaIdPropuesta");
            datos.remove("politicaNombrePropuesta");
            conv.setDatosRecopilados(datos);
            String msg = "Entendido. Cuentame que necesitas y te encuentro el tramite correcto.";
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", "DETECTANDO_POLITICA");
        }

        String politicaNombre = "";
        if (conv.getDatosRecopilados() != null) {
            politicaNombre = (String) conv.getDatosRecopilados().getOrDefault("politicaNombrePropuesta", "el tramite");
        }
        String msg = "Confirmas que quieres iniciar \"" + politicaNombre + "\"? Responde si o no.";
        agregarMensaje(conv, "agente", msg, "confirmacion");
        return Map.of("mensajeAgente", msg, "estado", "CONFIRMANDO_POLITICA");
    }

    // ─── Confirmacion final (arranca el tramite) ──────────────────────────────

    private Map<String, Object> manejarConfirmacionFinal(ConversacionAgente conv, String mensaje) {
        String msgLower = mensaje.toLowerCase().trim();
        boolean confirmo = msgLower.matches(".*(s[ií]|dale|ok|confirmo|adelante|listo|procede).*");

        if (!confirmo) {
            conv.setEstado(EstadoConversacion.DETECTANDO_POLITICA);
            conv.setPoliticaId(null);
            String msg = "Tramite cancelado. Si necesitas algo mas, estoy aqui.";
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", "DETECTANDO_POLITICA");
        }

        try {
            String politicaId = conv.getPoliticaId();
            String empresaId = conv.getEmpresaId();
            String clienteId = conv.getClienteId();
            String nombreTramite = conv.getNombreTramitePropuesto();

            Nodo nodoInicio = nodoRepository.findByPoliticaIdAndActivoTrue(politicaId).stream()
                    .filter(n -> "INICIO".equals(n.getTipo()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("La politica no tiene nodo INICIO"));

            Tramite tramite = Tramite.builder()
                    .politicaId(politicaId)
                    .empresaId(empresaId)
                    .titulo(nombreTramite != null ? nombreTramite : "Tramite del cliente")
                    .prioridad("MEDIA")
                    .estadoGeneral("PENDIENTE")
                    .iniciadoPor(clienteId)
                    .clienteId(clienteId)
                    .nodosParalelosPendientes(new ArrayList<>())
                    .iteracionesPorNodo(new HashMap<>())
                    .build();

            Tramite tramiteCreado = motorWorkflowService.iniciarTramite(tramite, nodoInicio.getId());

            conv.setTramiteId(tramiteCreado.getId());
            conv.setNodoActualId(tramiteCreado.getNodoActualId());
            conv.setEstado(EstadoConversacion.RECOPILANDO_DATOS_NODO);
            conv.setDatosRecopilados(new HashMap<>());
            conversacionRepository.save(conv);

            return iniciarRecopilacionNodo(conv);

        } catch (Exception e) {
            log.error("Error iniciando tramite para cliente {}: {}", conv.getClienteId(), e.getMessage());
            String msg = "Hubo un problema al iniciar tu tramite. Por favor intenta de nuevo.";
            agregarMensaje(conv, "agente", msg, "error");
            return Map.of("mensajeAgente", msg, "estado", conv.getEstado().name());
        }
    }

    // ─── Recopilacion de datos del nodo ──────────────────────────────────────

    private Map<String, Object> iniciarRecopilacionNodo(ConversacionAgente conv) {
        String nodoActualId = conv.getNodoActualId();

        if (nodoActualId == null) {
            // Buscar primer nodo TAREA de la politica con formulario de cliente
            List<Nodo> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(conv.getPoliticaId()).stream()
                    .filter(n -> "TAREA".equals(n.getTipo()))
                    .collect(Collectors.toList());

            for (Nodo nodo : nodos) {
                Optional<Formulario> formOpt = formularioRepository.findByNodoIdAndActivoTrue(nodo.getId());
                if (formOpt.isPresent() && tieneFieldsCliente(formOpt.get())) {
                    conv.setNodoActualId(nodo.getId());
                    nodoActualId = nodo.getId();
                    break;
                }
            }
        }

        if (nodoActualId == null) {
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            String msg = "Perfecto! He registrado tu solicitud. En breve un funcionario la revisara.";
            agregarMensaje(conv, "agente", msg, "estado");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        Optional<Formulario> formOpt = formularioRepository.findByNodoIdAndActivoTrue(nodoActualId);
        if (formOpt.isEmpty()) {
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            String msg = "Perfecto! He registrado tu solicitud. En breve un funcionario la revisara.";
            agregarMensaje(conv, "agente", msg, "estado");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        Formulario formulario = formOpt.get();
        List<Formulario.CampoFormulario> camposCliente = getCamposCliente(formulario);

        if (camposCliente.isEmpty()) {
            // No hay campos de cliente, enviar directamente
            return enviarFormularioYContinuar(conv, formulario);
        }

        // Determinar cual campo toca ahora
        Formulario.CampoFormulario campoActual = obtenerCampoActualPendiente(formulario, conv);
        if (campoActual == null) {
            return enviarFormularioYContinuar(conv, formulario);
        }

        // Si es ETIQUETA, saltar automaticamente
        if ("ETIQUETA".equals(campoActual.getTipo())) {
            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.put(campoActual.getNombre(), null);
            conv.setDatosRecopilados(datos);
            conversacionRepository.save(conv);
            return iniciarRecopilacionNodo(conv);
        }

        conv.setEstado(EstadoConversacion.RECOPILANDO_DATOS_NODO);
        String pregunta = generarPreguntaCampo(campoActual);
        int total = camposCliente.size();
        int idx = camposCliente.indexOf(campoActual);
        String preguntaConProgreso = "(" + (idx + 1) + "/" + total + ") " + pregunta;

        agregarMensaje(conv, "agente", preguntaConProgreso, "texto");
        return Map.of("mensajeAgente", preguntaConProgreso, "estado", "RECOPILANDO_DATOS_NODO",
                "campoActual", campoActual.getNombre());
    }

    private boolean tieneFieldsCliente(Formulario form) {
        if (form.getCampos() == null) return false;
        return form.getCampos().stream().anyMatch(c ->
                c.getLlenadoPor() == null || "CLIENTE".equals(c.getLlenadoPor().name()));
    }

    private List<Formulario.CampoFormulario> getCamposCliente(Formulario form) {
        if (form.getCampos() == null) return new ArrayList<>();
        return form.getCampos().stream()
                .filter(c -> c.getLlenadoPor() == null || "CLIENTE".equals(c.getLlenadoPor().name()))
                .collect(Collectors.toList());
    }

    private Formulario.CampoFormulario obtenerCampoActualPendiente(Formulario form, ConversacionAgente conv) {
        List<Formulario.CampoFormulario> camposCliente = getCamposCliente(form);
        Map<String, Object> datos = conv.getDatosRecopilados() != null
                ? conv.getDatosRecopilados() : new HashMap<>();

        for (Formulario.CampoFormulario campo : camposCliente) {
            String key = "__tabla_" + campo.getNombre();
            if (!datos.containsKey(campo.getNombre()) && !datos.containsKey(key)) {
                return campo;
            }
        }
        return null;
    }

    // ─── Generar pregunta segun tipo de campo ────────────────────────────────

    private String generarPreguntaCampo(Formulario.CampoFormulario campo) {
        String etiqueta = campo.getEtiqueta() != null ? campo.getEtiqueta() : campo.getNombre();
        String tipo = campo.getTipo() != null ? campo.getTipo() : "TEXTO";

        return switch (tipo) {
            case "TEXTO_CORTO", "TEXTO" ->
                    "Por favor, escribe tu " + etiqueta.toLowerCase() + ":";
            case "AREA_TEXTO", "TEXTAREA" ->
                    "Cuentame sobre " + etiqueta.toLowerCase() + " (puedes escribir todo lo que necesites):";
            case "ETIQUETA" -> null;
            case "NUMERO" ->
                    "Cual es tu " + etiqueta.toLowerCase() + "? (solo el numero)";
            case "FECHA" ->
                    "Cual es la " + etiqueta.toLowerCase() + "? (formato DD/MM/AAAA)";
            case "SELECTOR", "SELECCION" -> {
                List<String> opciones = campo.getOpciones() != null ? campo.getOpciones() : new ArrayList<>();
                String listaOpciones = IntStream.range(0, opciones.size())
                        .mapToObj(i -> (i + 1) + ". " + opciones.get(i))
                        .collect(Collectors.joining("\n"));
                yield "Para " + etiqueta + ", elige una opcion:\n" + listaOpciones;
            }
            case "RADIO" -> {
                List<String> opciones = campo.getOpciones() != null ? campo.getOpciones() : new ArrayList<>();
                String listaOpciones = opciones.stream()
                        .map(op -> "- " + op)
                        .collect(Collectors.joining("\n"));
                yield "Selecciona una opcion para " + etiqueta + ":\n" + listaOpciones;
            }
            case "CHECKBOX" -> {
                List<String> opciones = campo.getOpciones() != null ? campo.getOpciones() : new ArrayList<>();
                String listaOpciones = IntStream.range(0, opciones.size())
                        .mapToObj(i -> (i + 1) + ". " + opciones.get(i))
                        .collect(Collectors.joining("\n"));
                yield "Selecciona todas las que apliquen para " + etiqueta + ":\n" + listaOpciones
                        + "\n(Escribe los numeros separados por coma, ej: 1, 3)";
            }
            case "ARCHIVO" ->
                    "Necesito que adjuntes: " + etiqueta + "\nFormatos aceptados: PDF, Word, Excel, imagen";
            case "IMAGEN" ->
                    "Por favor sube una foto de: " + etiqueta;
            case "TABLA_GRID", "GRID" ->
                    generarPreguntaTablaGrid(campo);
            default ->
                    "Por favor, ingresa tu " + etiqueta.toLowerCase() + ":";
        };
    }

    private String generarPreguntaTablaGrid(Formulario.CampoFormulario campo) {
        String etiqueta = campo.getEtiqueta() != null ? campo.getEtiqueta() : campo.getNombre();
        List<String> columnas = campo.getColumnas() != null ? campo.getColumnas() : new ArrayList<>();
        String colStr = columnas.isEmpty() ? "Columna1 | Columna2" : String.join(" | ", columnas);

        return "Necesito que completes una tabla para " + etiqueta + ".\n\n"
                + "La tabla tiene estas columnas: " + colStr + "\n\n"
                + "Ingresa cada fila asi:\nvalor1, valor2, valor3\n\n"
                + "Cuando termines todas las filas, escribe 'listo'.\n"
                + "O si prefieres, puedes subir el archivo Excel directamente.";
    }

    // ─── Manejar respuesta del campo ─────────────────────────────────────────

    private Map<String, Object> manejarRespuestaCampo(ConversacionAgente conv, String mensaje) {
        String nodoActualId = conv.getNodoActualId();
        if (nodoActualId == null) {
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            String msg = "He registrado tu informacion. Tu solicitud sera revisada en breve.";
            agregarMensaje(conv, "agente", msg, "estado");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        Optional<Formulario> formOpt = formularioRepository.findByNodoIdAndActivoTrue(nodoActualId);
        if (formOpt.isEmpty()) {
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            String msg = "He registrado tu solicitud. En breve te contactaremos.";
            agregarMensaje(conv, "agente", msg, "estado");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        Formulario formulario = formOpt.get();
        Formulario.CampoFormulario campoActual = obtenerCampoActualPendiente(formulario, conv);

        if (campoActual == null) {
            return enviarFormularioYContinuar(conv, formulario);
        }

        String tipo = campoActual.getTipo() != null ? campoActual.getTipo() : "TEXTO";

        // Caso especial: ETIQUETA — saltar sin preguntar
        if ("ETIQUETA".equals(tipo)) {
            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.put(campoActual.getNombre(), null);
            conv.setDatosRecopilados(datos);
            conversacionRepository.save(conv);
            return iniciarRecopilacionNodo(conv);
        }

        // Caso especial: TABLA_GRID
        if ("TABLA_GRID".equals(tipo) || "GRID".equals(tipo)) {
            return manejarRespuestaTablaGrid(conv, formulario, campoActual, mensaje);
        }

        // Caso especial: CHECKBOX
        if ("CHECKBOX".equals(tipo)) {
            return manejarRespuestaCheckbox(conv, formulario, campoActual, mensaje);
        }

        // Caso especial: ARCHIVO / IMAGEN — registrar URL directamente
        if ("ARCHIVO".equals(tipo) || "IMAGEN".equals(tipo)) {
            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.put(campoActual.getNombre(), mensaje);
            conv.setDatosRecopilados(datos);
            conversacionRepository.save(conv);
            return iniciarRecopilacionNodo(conv);
        }

        // Caso general: llamar al microservicio IA para validar/extraer
        try {
            Map<String, Object> campoDto = new HashMap<>();
            campoDto.put("nombre", campoActual.getNombre());
            campoDto.put("etiqueta", campoActual.getEtiqueta());
            campoDto.put("tipo", tipo);
            campoDto.put("opciones", campoActual.getOpciones() != null ? campoActual.getOpciones() : new ArrayList<>());
            campoDto.put("requerido", Boolean.TRUE.equals(campoActual.getRequerido()));

            Map<String, Object> iaRequest = Map.of("campo", campoDto, "respuesta_cliente", mensaje);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(iaRequest, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> iaResponse = restTemplate.postForObject(
                    iaServiceUrl + "/ia/agente/procesar-campo", entity, Map.class);

            if (iaResponse != null) {
                Boolean valorValido = (Boolean) iaResponse.getOrDefault("valor_valido", true);
                Boolean necesitaRepetir = (Boolean) iaResponse.getOrDefault("necesita_repetir", false);
                Object valorExtraido = iaResponse.get("valor_extraido");
                String mensajeParaCliente = (String) iaResponse.get("mensaje_para_cliente");

                if (Boolean.TRUE.equals(necesitaRepetir) || Boolean.FALSE.equals(valorValido)) {
                    String msg = mensajeParaCliente != null ? mensajeParaCliente
                            : "Por favor proporciona un valor valido para: " + campoActual.getEtiqueta();
                    agregarMensaje(conv, "agente", msg, "texto");
                    return Map.of("mensajeAgente", msg, "estado", "RECOPILANDO_DATOS_NODO");
                }

                Map<String, Object> datos = conv.getDatosRecopilados() != null
                        ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
                datos.put(campoActual.getNombre(), valorExtraido != null ? valorExtraido : mensaje);
                conv.setDatosRecopilados(datos);
            } else {
                Map<String, Object> datos = conv.getDatosRecopilados() != null
                        ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
                datos.put(campoActual.getNombre(), mensaje);
                conv.setDatosRecopilados(datos);
            }
        } catch (Exception e) {
            log.debug("IA no disponible para procesar campo, usando valor directo: {}", e.getMessage());
            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.put(campoActual.getNombre(), mensaje);
            conv.setDatosRecopilados(datos);
        }

        conversacionRepository.save(conv);
        return iniciarRecopilacionNodo(conv);
    }

    // ─── Manejar TABLA_GRID ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> manejarRespuestaTablaGrid(ConversacionAgente conv, Formulario form,
                                                           Formulario.CampoFormulario campo, String mensaje) {
        String msgLower = mensaje.toLowerCase().trim();
        String keyAcumulador = "__tabla_" + campo.getNombre();

        Map<String, Object> datos = conv.getDatosRecopilados() != null
                ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();

        List<List<String>> filasAcumuladas = datos.get(keyAcumulador) instanceof List
                ? (List<List<String>>) datos.get(keyAcumulador) : new ArrayList<>();

        if (msgLower.equals("listo") || msgLower.equals("fin") || msgLower.equals("termine")) {
            if (filasAcumuladas.isEmpty()) {
                String msg = "No ingresaste ninguna fila. Ingresa al menos una fila o sube el archivo.";
                agregarMensaje(conv, "agente", msg, "texto");
                return Map.of("mensajeAgente", msg, "estado", "RECOPILANDO_DATOS_NODO");
            }

            List<String> columnas = campo.getColumnas() != null ? campo.getColumnas() : new ArrayList<>();
            List<Map<String, String>> datosTabla = filasAcumuladas.stream()
                    .map(fila -> {
                        Map<String, String> mapa = new LinkedHashMap<>();
                        for (int i = 0; i < Math.min(columnas.size(), fila.size()); i++) {
                            mapa.put(columnas.get(i), fila.get(i));
                        }
                        return mapa;
                    }).collect(Collectors.toList());

            datos.put(campo.getNombre(), datosTabla);
            datos.remove(keyAcumulador);
            conv.setDatosRecopilados(datos);
            conversacionRepository.save(conv);
            return iniciarRecopilacionNodo(conv);
        }

        String[] valores = mensaje.split(",");
        List<String> fila = Arrays.stream(valores)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .collect(Collectors.toList());

        if (!fila.isEmpty()) {
            filasAcumuladas.add(fila);
            datos.put(keyAcumulador, filasAcumuladas);
            conv.setDatosRecopilados(datos);
            conversacionRepository.save(conv);

            int numFila = filasAcumuladas.size();
            List<String> columnas = campo.getColumnas() != null ? campo.getColumnas() : new ArrayList<>();
            String colStr = String.join(" | ", columnas);

            String msg = String.format("Fila %d registrada: %s\n\nIngresa otra fila (%s) o escribe 'listo' cuando termines.",
                    numFila, String.join(", ", fila), colStr);
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", "RECOPILANDO_DATOS_NODO");
        }

        String msg = "No entendi esa fila. Ingresa los valores separados por coma. Ej: valor1, valor2";
        agregarMensaje(conv, "agente", msg, "texto");
        return Map.of("mensajeAgente", msg, "estado", "RECOPILANDO_DATOS_NODO");
    }

    // ─── Manejar CHECKBOX ────────────────────────────────────────────────────

    private Map<String, Object> manejarRespuestaCheckbox(ConversacionAgente conv, Formulario form,
                                                          Formulario.CampoFormulario campo, String mensaje) {
        List<String> opciones = campo.getOpciones() != null ? campo.getOpciones() : new ArrayList<>();
        List<String> seleccionadas = new ArrayList<>();

        String[] partes = mensaje.split("[,\\s]+");
        for (String parte : partes) {
            parte = parte.trim();
            if (parte.isBlank()) continue;
            try {
                int idx = Integer.parseInt(parte) - 1;
                if (idx >= 0 && idx < opciones.size()) {
                    seleccionadas.add(opciones.get(idx));
                }
            } catch (NumberFormatException e) {
                String finalParte = parte;
                opciones.stream()
                        .filter(op -> op.toLowerCase().contains(finalParte.toLowerCase()))
                        .findFirst()
                        .ifPresent(seleccionadas::add);
            }
        }

        if (seleccionadas.isEmpty()) {
            String msg = "No entendi tu seleccion. Escribe los numeros de las opciones separados por coma. Ej: 1, 3";
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", "RECOPILANDO_DATOS_NODO");
        }

        Map<String, Object> datos = conv.getDatosRecopilados() != null
                ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
        datos.put(campo.getNombre(), seleccionadas);
        conv.setDatosRecopilados(datos);
        conversacionRepository.save(conv);
        return iniciarRecopilacionNodo(conv);
    }

    // ─── Enviar formulario y continuar con el motor ───────────────────────────

    private Map<String, Object> enviarFormularioYContinuar(ConversacionAgente conv, Formulario form) {
        try {
            motorWorkflowService.clienteCompletadoNodo(
                    conv.getTramiteId(),
                    conv.getNodoActualId(),
                    conv.getDatosRecopilados() != null ? conv.getDatosRecopilados() : new HashMap<>()
            );
        } catch (Exception e) {
            log.warn("clienteCompletadoNodo fallo (puede que no haya ejecucion en fase CLIENTE): {}", e.getMessage());
        }

        conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
        conv.setDatosRecopilados(new HashMap<>());

        String departamento = "el equipo";
        if (conv.getNodoActualId() != null) {
            Nodo nodo = nodoRepository.findById(conv.getNodoActualId()).orElse(null);
            if (nodo != null && nodo.getDepartamentoId() != null) {
                departamento = departamentoRepository.findById(nodo.getDepartamentoId())
                        .map(Departamento::getNombre)
                        .orElse("el equipo");
            }
        }

        String msg = String.format(
                "Listo! Toda tu informacion fue enviada correctamente.\n\n" +
                "El equipo de %s esta revisando tu solicitud. Te notificaremos aqui en cuanto haya una respuesta.",
                departamento);
        agregarMensaje(conv, "agente", msg, "estado");

        if (conv.getClienteId() != null) {
            notificacionService.crearNotificacion(
                    conv.getClienteId(),
                    conv.getTramiteId(),
                    conv.getNodoActualId(),
                    "SOLICITUD_ENVIADA",
                    "Tu solicitud fue enviada y esta en revision."
            );
        }

        return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION", "formularioCompletado", true);
    }

    // ─── Archivo subido ───────────────────────────────────────────────────────

    public Map<String, Object> procesarArchivoSubido(String conversacionId, String clienteId,
                                                      String archivoUrl, String nombreArchivo) {
        ConversacionAgente conv = conversacionRepository.findById(conversacionId)
                .orElseGet(() -> crearNuevaConversacion(clienteId));

        List<String> archivos = conv.getArchivosSubidos() != null
                ? new ArrayList<>(conv.getArchivosSubidos()) : new ArrayList<>();
        archivos.add(archivoUrl);
        conv.setArchivosSubidos(archivos);

        // Guardar el archivo en el campo correspondiente si estamos recopilando
        if (conv.getNodoActualId() != null &&
                (EstadoConversacion.ESPERANDO_ARCHIVOS.equals(conv.getEstado())
                 || EstadoConversacion.RECOPILANDO_DATOS_NODO.equals(conv.getEstado()))) {

            Optional<Formulario> formOpt = formularioRepository.findByNodoIdAndActivoTrue(conv.getNodoActualId());
            formOpt.ifPresent(formulario -> {
                Formulario.CampoFormulario campo = obtenerCampoActualPendiente(formulario, conv);
                if (campo != null) {
                    Map<String, Object> datos = conv.getDatosRecopilados() != null
                            ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
                    datos.put(campo.getNombre(), archivoUrl);
                    conv.setDatosRecopilados(datos);
                }
            });
        }

        if (EstadoConversacion.ESPERANDO_ARCHIVOS.equals(conv.getEstado())) {
            conv.setEstado(EstadoConversacion.RECOPILANDO_DATOS_NODO);
        }

        Map<String, Object> respuesta = manejarRespuestaCampo(conv, "[archivo:" + nombreArchivo + "]");
        return guardarYRetornar(conv, respuesta);
    }

    // ─── Notificar cliente sobre decision del motor ───────────────────────────

    public void notificarClienteDecision(String tramiteId, String decision, String nodoSiguienteId) {
        conversacionRepository.findByTramiteId(tramiteId).ifPresent(conv -> {

            switch (decision) {
                case "RECHAZADO" -> {
                    conv.setEstado(EstadoConversacion.RECHAZADO);
                    conv.setUltimaActividadEn(LocalDateTime.now());
                    String msg = "Tu solicitud fue rechazada. Puedes ver los detalles en tu historial o iniciar un nuevo tramite.";
                    agregarMensaje(conv, "agente", msg, "estado");
                    conversacionRepository.save(conv);
                    enviarNotificacionWsCliente(conv.getClienteId(), msg);
                }
                case "COMPLETADO" -> {
                    conv.setEstado(EstadoConversacion.COMPLETADO);
                    conv.setUltimaActividadEn(LocalDateTime.now());
                    String msg = "Tu tramite fue completado exitosamente! Puedes ver el resumen en 'Mis Tramites'.";
                    agregarMensaje(conv, "agente", msg, "estado");
                    conversacionRepository.save(conv);
                    enviarNotificacionWsCliente(conv.getClienteId(), msg);
                }
                case "APROBADO_SIGUIENTE_NODO" -> {
                    conv.setNodoActualId(nodoSiguienteId);
                    conv.setDatosRecopilados(new HashMap<>());
                    conv.setEstado(EstadoConversacion.RECOPILANDO_DATOS_NODO);
                    conv.setUltimaActividadEn(LocalDateTime.now());
                    conversacionRepository.save(conv);

                    String msgNotif = "Tu solicitud fue aprobada. Necesito algunos datos adicionales para continuar.";
                    enviarNotificacionWsCliente(conv.getClienteId(), msgNotif);

                    // Iniciar recopilacion del nuevo nodo
                    Optional<Formulario> formOpt = formularioRepository.findByNodoIdAndActivoTrue(nodoSiguienteId);
                    if (formOpt.isPresent()) {
                        Map<String, Object> resp = iniciarRecopilacionNodo(conv);
                        guardarYRetornar(conv, resp);
                    }
                }
                case "TRAMITE_EN_PROCESO" -> {
                    conv.setEstado(EstadoConversacion.TRAMITE_EN_PROCESO);
                    conv.setUltimaActividadEn(LocalDateTime.now());

                    String depto = "el siguiente equipo";
                    if (nodoSiguienteId != null) {
                        Nodo nodo = nodoRepository.findById(nodoSiguienteId).orElse(null);
                        if (nodo != null && nodo.getDepartamentoId() != null) {
                            depto = departamentoRepository.findById(nodo.getDepartamentoId())
                                    .map(Departamento::getNombre).orElse("el siguiente equipo");
                        }
                    }
                    String msg = "Tu tramite continua en " + depto + ". Te avisaremos cuando haya novedades.";
                    agregarMensaje(conv, "agente", msg, "estado");
                    conversacionRepository.save(conv);
                    enviarNotificacionWsCliente(conv.getClienteId(), msg);
                }
                default -> {
                    // Fallback para compatibilidad con llamadas anteriores (decision = "APROBADO")
                    String msg = "Tu solicitud fue aprobada en este paso. Continua al siguiente.";
                    conv.setEstado(EstadoConversacion.TRAMITE_EN_PROCESO);
                    if (nodoSiguienteId != null) conv.setNodoActualId(nodoSiguienteId);
                    conv.setUltimaActividadEn(LocalDateTime.now());
                    agregarMensaje(conv, "agente", msg, "estado");
                    conversacionRepository.save(conv);
                    enviarNotificacionWsCliente(conv.getClienteId(), msg);
                }
            }
        });
    }

    private void enviarNotificacionWsCliente(String clienteId, String mensaje) {
        if (clienteId != null && !clienteId.isBlank()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tipo", "MENSAJE_AGENTE");
            payload.put("mensaje", mensaje);
            payload.put("timestamp", LocalDateTime.now().toString());
            notificacionService.notificarUsuario(clienteId, payload);
        }
    }

    // ─── Estado del tramite ────────────────────────────────────────────────────

    public EstadoTramiteClienteResponse obtenerEstadoTramite(String tramiteId) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new RuntimeException("Tramite no encontrado"));

        String nodoNombre = null;
        String departamentoNombre = null;

        if (tramite.getNodoActualId() != null) {
            Optional<Nodo> nodoOpt = nodoRepository.findById(tramite.getNodoActualId());
            if (nodoOpt.isPresent()) {
                nodoNombre = nodoOpt.get().getNombre();
                String deptoId = nodoOpt.get().getDepartamentoId();
                if (deptoId != null) {
                    departamentoNombre = departamentoRepository.findById(deptoId)
                            .map(Departamento::getNombre).orElse(deptoId);
                }
            }
        }

        String mensajeEstado = switch (tramite.getEstadoGeneral()) {
            case "PENDIENTE" -> "Tu tramite esta pendiente de revision.";
            case "EN_PROCESO" -> "Tu tramite esta siendo procesado" +
                    (departamentoNombre != null ? " por " + departamentoNombre : "") + ".";
            case "COMPLETADO" -> "Tu tramite fue completado exitosamente.";
            case "RECHAZADO" -> "Tu tramite fue rechazado. Contacta a CRE para mas informacion.";
            case "BLOQUEADO" -> "Tu tramite esta temporalmente bloqueado. El equipo de CRE lo revisara.";
            default -> "Estado: " + tramite.getEstadoGeneral();
        };

        return EstadoTramiteClienteResponse.builder()
                .tramiteId(tramite.getId())
                .titulo(tramite.getTitulo())
                .estadoGeneral(tramite.getEstadoGeneral())
                .nodoActualNombre(nodoNombre)
                .departamentoActualNombre(departamentoNombre)
                .mensajeEstado(mensajeEstado)
                .prioridad(tramite.getPrioridad())
                .iniciadoEn(tramite.getIniciadoEn() != null ? tramite.getIniciadoEn().toString() : null)
                .build();
    }

    // ─── Historial de conversacion ─────────────────────────────────────────────

    public List<ConversacionAgente> obtenerHistorialCliente(String clienteId) {
        return conversacionRepository.findByClienteId(clienteId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ConversacionAgente crearNuevaConversacion(String clienteId) {
        ConversacionAgente conv = ConversacionAgente.builder()
                .clienteId(clienteId)
                .estado(EstadoConversacion.DETECTANDO_POLITICA)
                .mensajes(new ArrayList<>())
                .datosRecopilados(new HashMap<>())
                .archivosSubidos(new ArrayList<>())
                .creadoEn(LocalDateTime.now())
                .ultimaActividadEn(LocalDateTime.now())
                .build();
        return conversacionRepository.save(conv);
    }

    private void agregarMensaje(ConversacionAgente conv, String rol, String contenido, String tipo) {
        if (conv.getMensajes() == null) {
            conv.setMensajes(new ArrayList<>());
        }
        conv.getMensajes().add(MensajeChat.builder()
                .rol(rol)
                .contenido(contenido)
                .tipo(tipo)
                .timestamp(LocalDateTime.now())
                .build());
        conv.setUltimaActividadEn(LocalDateTime.now());
    }

    private String obtenerMensajeEstadoActual(ConversacionAgente conv) {
        if (conv.getTramiteId() != null) {
            return tramiteRepository.findById(conv.getTramiteId())
                    .map(t -> "Tu tramite '" + t.getTitulo() + "' esta en estado: " + t.getEstadoGeneral() + ".")
                    .orElse("Tu tramite esta en proceso de revision.");
        }
        return "Tu solicitud fue enviada y esta siendo revisada por nuestro equipo.";
    }

    private Map<String, Object> respuestaError(ConversacionAgente conv, String mensaje) {
        agregarMensaje(conv, "agente", mensaje, "texto");
        return Map.of("mensajeAgente", mensaje, "estado", conv.getEstado().name());
    }

    private Map<String, Object> guardarYRetornar(ConversacionAgente conv, Map<String, Object> respuesta) {
        conversacionRepository.save(conv);
        Map<String, Object> resultado = new HashMap<>(respuesta);
        resultado.put("conversacionId", conv.getId());
        resultado.put("estadoConversacion", conv.getEstado().name());
        return resultado;
    }
}
