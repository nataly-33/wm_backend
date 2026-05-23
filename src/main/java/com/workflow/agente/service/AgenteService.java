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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final RestTemplate restTemplate;

    @Value("${ia.service.url:http://localhost:8001}")
    private String iaServiceUrl;

    // ─── Procesar mensaje del cliente ─────────────────────────────────────────

    public Map<String, Object> procesarMensaje(String conversacionId, String clienteId, String mensaje, String tipo) {
        ConversacionAgente conversacion;

        if (conversacionId != null && !conversacionId.isBlank()) {
            conversacion = conversacionRepository.findById(conversacionId)
                    .orElse(crearNuevaConversacion(clienteId));
        } else {
            // Buscar conversacion activa o crear nueva
            conversacion = conversacionRepository
                    .findByClienteIdAndEstadoNot(clienteId, EstadoConversacion.COMPLETADO)
                    .orElse(crearNuevaConversacion(clienteId));
        }

        // Agregar mensaje del cliente al historial
        agregarMensaje(conversacion, "cliente", mensaje, tipo != null ? tipo : "texto");

        Map<String, Object> respuesta;
        switch (conversacion.getEstado()) {
            case DETECTANDO_POLITICA -> respuesta = manejarDeteccionPolitica(conversacion, mensaje, clienteId);
            case CONFIRMANDO_POLITICA -> respuesta = manejarConfirmacion(conversacion, mensaje);
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

    private Map<String, Object> manejarDeteccionPolitica(ConversacionAgente conv, String mensaje, String clienteId) {
        // Obtener empresaId desde algun tramite previo del cliente o usar busqueda general
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
            // Llamar al microservicio IA para detectar la politica
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
            Boolean necesitaMasInfo = (Boolean) iaResponse.getOrDefault("necesita_mas_info", true);
            @SuppressWarnings("unchecked")
            Map<String, Object> politicaSugerida = (Map<String, Object>) iaResponse.get("politica_sugerida");

            if (Boolean.TRUE.equals(necesitaMasInfo) || politicaSugerida == null) {
                agregarMensaje(conv, "agente", mensajeCliente != null ? mensajeCliente
                        : "No entendi bien tu solicitud. Puedes decirme que tramite necesitas?", "texto");
                return Map.of("mensajeAgente", mensajeCliente != null ? mensajeCliente
                        : "No entendi bien tu solicitud.", "estado", conv.getEstado().name());
            }

            // Politica detectada — pasar a confirmacion
            String politicaId = (String) politicaSugerida.get("id");
            String nombrePolitica = (String) politicaSugerida.get("nombre");

            Map<String, Object> datos = conv.getDatosRecopilados() != null
                    ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
            datos.put("politicaIdPropuesta", politicaId);
            datos.put("politicaNombrePropuesta", nombrePolitica);
            conv.setDatosRecopilados(datos);
            conv.setEstado(EstadoConversacion.CONFIRMANDO_POLITICA);

            String msg = mensajeCliente != null ? mensajeCliente
                    : "Detecto que necesitas: " + nombrePolitica + ". Es correcto?";
            agregarMensaje(conv, "agente", msg, "confirmacion");

            return Map.of("mensajeAgente", msg, "estado", "CONFIRMANDO_POLITICA",
                    "politicaDetectada", nombrePolitica);

        } catch (Exception e) {
            log.warn("Error llamando a IA para detectar politica: {}", e.getMessage());
            // Fallback: listar politicas disponibles
            String lista = politicas.stream()
                    .map(p -> "- " + p.getNombre())
                    .collect(Collectors.joining("\n"));
            String msg = "Hola! Soy el asistente de CRE. Puedo ayudarte con los siguientes tramites:\n" + lista
                    + "\n\nPor favor dime cual necesitas.";
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", conv.getEstado().name());
        }
    }

    private Map<String, Object> manejarConfirmacion(ConversacionAgente conv, String mensaje) {
        String textoLower = mensaje.toLowerCase().trim();
        boolean confirmo = textoLower.contains("si") || textoLower.contains("yes")
                || textoLower.contains("correcto") || textoLower.contains("exacto")
                || textoLower.contains("claro") || textoLower.contains("ok");
        boolean rechazo = textoLower.contains("no") || textoLower.contains("otro")
                || textoLower.contains("diferente") || textoLower.contains("error");

        Map<String, Object> datos = conv.getDatosRecopilados() != null
                ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();

        if (confirmo) {
            String politicaId = (String) datos.get("politicaIdPropuesta");
            if (politicaId == null) {
                conv.setEstado(EstadoConversacion.DETECTANDO_POLITICA);
                String msg = "Ocurrio un error. Por favor cuentame de nuevo que tramite necesitas.";
                agregarMensaje(conv, "agente", msg, "texto");
                return Map.of("mensajeAgente", msg, "estado", "DETECTANDO_POLITICA");
            }
            conv.setPoliticaId(politicaId);
            conv.setDatosRecopilados(datos);
            return iniciarRecopilacionNodo(conv);
        } else if (rechazo) {
            datos.remove("politicaIdPropuesta");
            datos.remove("politicaNombrePropuesta");
            conv.setDatosRecopilados(datos);
            conv.setEstado(EstadoConversacion.DETECTANDO_POLITICA);
            String msg = "Entendido. Cuentame que tramite necesitas y con gusto te ayudo.";
            agregarMensaje(conv, "agente", msg, "texto");
            return Map.of("mensajeAgente", msg, "estado", "DETECTANDO_POLITICA");
        } else {
            String politicaNombre = (String) datos.getOrDefault("politicaNombrePropuesta", "el tramite");
            String msg = "Por favor confirma: quieres iniciar " + politicaNombre + "? Responde si o no.";
            agregarMensaje(conv, "agente", msg, "confirmacion");
            return Map.of("mensajeAgente", msg, "estado", "CONFIRMANDO_POLITICA");
        }
    }

    private Map<String, Object> iniciarRecopilacionNodo(ConversacionAgente conv) {
        // Buscar el primer nodo TAREA de la politica que tenga formulario
        List<Nodo> nodos = nodoRepository.findByPoliticaIdAndActivoTrue(conv.getPoliticaId()).stream()
                .filter(n -> "TAREA".equals(n.getTipo()))
                .collect(Collectors.toList());

        if (nodos.isEmpty()) {
            // No hay nodos con formulario — crear tramite directamente
            String msg = "Perfecto! Vamos a iniciar tu tramite. Un momento por favor.";
            agregarMensaje(conv, "agente", msg, "texto");
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        // Buscar el primer nodo con formulario activo
        for (Nodo nodo : nodos) {
            Optional<Formulario> formularioOpt = formularioRepository.findByNodoIdAndActivoTrue(nodo.getId());
            if (formularioOpt.isPresent()) {
                Formulario formulario = formularioOpt.get();
                if (formulario.getCampos() != null && !formulario.getCampos().isEmpty()) {
                    conv.setNodoActualId(nodo.getId());
                    Map<String, Object> datos = conv.getDatosRecopilados() != null
                            ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();
                    datos.put("formularioId", formulario.getId());
                    datos.put("campoIndex", 0);
                    conv.setDatosRecopilados(datos);
                    conv.setEstado(EstadoConversacion.RECOPILANDO_DATOS_NODO);

                    String pregunta = generarPreguntaCampo(formulario.getCampos().get(0), 0, formulario.getCampos().size());
                    agregarMensaje(conv, "agente", pregunta, "texto");
                    return Map.of("mensajeAgente", pregunta, "estado", "RECOPILANDO_DATOS_NODO",
                            "campoActual", formulario.getCampos().get(0).getNombre());
                }
            }
        }

        // Ningun formulario encontrado — avanzar
        conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
        String msg = "Perfecto! He registrado tu solicitud. En breve un funcionario la revisara.";
        agregarMensaje(conv, "agente", msg, "estado");
        return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
    }

    private String generarPreguntaCampo(Formulario.CampoFormulario campo, int indice, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(indice + 1).append("/").append(total).append(") ");
        sb.append(campo.getEtiqueta() != null ? campo.getEtiqueta() : campo.getNombre());
        if (campo.getOpciones() != null && !campo.getOpciones().isEmpty()) {
            sb.append("\nOpciones: ").append(String.join(", ", campo.getOpciones()));
        }
        if (Boolean.FALSE.equals(campo.getRequerido())) {
            sb.append(" (opcional)");
        }
        return sb.toString();
    }

    private Map<String, Object> manejarRespuestaCampo(ConversacionAgente conv, String respuestaCliente) {
        Map<String, Object> datos = conv.getDatosRecopilados() != null
                ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();

        String formularioId = (String) datos.get("formularioId");
        int campoIndex = datos.get("campoIndex") instanceof Number
                ? ((Number) datos.get("campoIndex")).intValue() : 0;

        if (formularioId == null) {
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            String msg = "He registrado tu informacion. Tu solicitud sera revisada en breve.";
            agregarMensaje(conv, "agente", msg, "estado");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        Optional<Formulario> formOpt = formularioRepository.findById(formularioId);
        if (formOpt.isEmpty()) {
            conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
            String msg = "He registrado tu solicitud. En breve te contactaremos.";
            agregarMensaje(conv, "agente", msg, "estado");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION");
        }

        Formulario formulario = formOpt.get();
        List<Formulario.CampoFormulario> campos = formulario.getCampos();

        if (campoIndex >= campos.size()) {
            return enviarFormularioYContinuar(conv, datos);
        }

        Formulario.CampoFormulario campoActual = campos.get(campoIndex);

        try {
            // Procesar con IA si disponible
            Map<String, Object> campoDto = new HashMap<>();
            campoDto.put("nombre", campoActual.getNombre());
            campoDto.put("etiqueta", campoActual.getEtiqueta());
            campoDto.put("tipo", campoActual.getTipo());
            campoDto.put("opciones", campoActual.getOpciones() != null ? campoActual.getOpciones() : new ArrayList<>());
            campoDto.put("requerido", Boolean.TRUE.equals(campoActual.getRequerido()));

            Map<String, Object> iaRequest = Map.of("campo", campoDto, "respuesta_cliente", respuestaCliente);
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

                if (valorExtraido != null) {
                    datos.put(campoActual.getNombre(), valorExtraido);
                }
            } else {
                datos.put(campoActual.getNombre(), respuestaCliente);
            }
        } catch (Exception e) {
            log.debug("IA no disponible para procesar campo, usando valor directo: {}", e.getMessage());
            datos.put(campoActual.getNombre(), respuestaCliente);
        }

        // Avanzar al siguiente campo
        int siguienteIndex = campoIndex + 1;
        datos.put("campoIndex", siguienteIndex);
        conv.setDatosRecopilados(datos);

        if (siguienteIndex >= campos.size()) {
            return enviarFormularioYContinuar(conv, datos);
        }

        Formulario.CampoFormulario siguienteCampo = campos.get(siguienteIndex);
        if ("ARCHIVO".equals(siguienteCampo.getTipo()) || "IMAGEN".equals(siguienteCampo.getTipo())) {
            conv.setEstado(EstadoConversacion.ESPERANDO_ARCHIVOS);
            String msg = "Necesito que subas: " + siguienteCampo.getEtiqueta()
                    + ". Usa el boton de adjuntar para subir el archivo.";
            agregarMensaje(conv, "agente", msg, "archivo");
            return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_ARCHIVOS",
                    "campoActual", siguienteCampo.getNombre());
        }

        String pregunta = generarPreguntaCampo(siguienteCampo, siguienteIndex, campos.size());
        agregarMensaje(conv, "agente", pregunta, "texto");
        return Map.of("mensajeAgente", pregunta, "estado", "RECOPILANDO_DATOS_NODO",
                "campoActual", siguienteCampo.getNombre());
    }

    private Map<String, Object> enviarFormularioYContinuar(ConversacionAgente conv, Map<String, Object> datos) {
        conv.setEstado(EstadoConversacion.ESPERANDO_APROBACION);
        conv.setDatosRecopilados(datos);

        String msg = "Listo! He recibido toda la informacion. Tu solicitud fue enviada y sera revisada por nuestro equipo. "
                + "Te notificaremos cuando haya novedades.";
        agregarMensaje(conv, "agente", msg, "estado");

        // Notificar al cliente
        if (conv.getClienteId() != null) {
            notificacionService.crearNotificacion(
                    conv.getClienteId(),
                    conv.getTramiteId(),
                    conv.getNodoActualId(),
                    "SOLICITUD_ENVIADA",
                    "Tu solicitud fue enviada y esta en revision."
            );
        }

        return Map.of("mensajeAgente", msg, "estado", "ESPERANDO_APROBACION",
                "formularioCompletado", true);
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

        Map<String, Object> datos = conv.getDatosRecopilados() != null
                ? new HashMap<>(conv.getDatosRecopilados()) : new HashMap<>();

        // Guardar el archivo en el campo correspondiente
        String formularioId = (String) datos.get("formularioId");
        int campoIndex = datos.get("campoIndex") instanceof Number
                ? ((Number) datos.get("campoIndex")).intValue() : 0;

        if (formularioId != null) {
            formularioRepository.findById(formularioId).ifPresent(formulario -> {
                List<Formulario.CampoFormulario> campos = formulario.getCampos();
                if (campoIndex < campos.size()) {
                    Formulario.CampoFormulario campo = campos.get(campoIndex);
                    datos.put(campo.getNombre(), archivoUrl);
                    datos.put("campoIndex", campoIndex + 1);
                }
            });
        }

        conv.setDatosRecopilados(datos);

        // Continuar con siguiente campo
        if (EstadoConversacion.ESPERANDO_ARCHIVOS.equals(conv.getEstado())) {
            conv.setEstado(EstadoConversacion.RECOPILANDO_DATOS_NODO);
            Map<String, Object> respuesta = manejarRespuestaCampo(conv, "[archivo:" + nombreArchivo + "]");
            return guardarYRetornar(conv, respuesta);
        }

        String msg = "Archivo '" + nombreArchivo + "' recibido correctamente.";
        agregarMensaje(conv, "agente", msg, "archivo");
        Map<String, Object> respuesta = Map.of("mensajeAgente", msg, "estado", conv.getEstado().name());
        return guardarYRetornar(conv, respuesta);
    }

    // ─── Notificar cliente sobre decision ─────────────────────────────────────

    public void notificarClienteDecision(String tramiteId, String decision, String nodoSiguienteId) {
        // Buscar conversacion por tramiteId
        List<ConversacionAgente> convs = conversacionRepository.findAll().stream()
                .filter(c -> tramiteId.equals(c.getTramiteId()))
                .collect(Collectors.toList());

        for (ConversacionAgente conv : convs) {
            String mensajeDecision;
            EstadoConversacion nuevoEstado;

            if ("RECHAZADO".equals(decision)) {
                mensajeDecision = "Lamentamos informarte que tu solicitud fue rechazada. "
                        + "Por favor contacta a CRE para mas informacion.";
                nuevoEstado = EstadoConversacion.RECHAZADO;
            } else if ("COMPLETADO".equals(decision)) {
                mensajeDecision = "Tu tramite ha sido completado exitosamente. "
                        + "Puedes ver el resumen en tu historial.";
                nuevoEstado = EstadoConversacion.COMPLETADO;
            } else {
                mensajeDecision = "Tu solicitud fue aprobada en este paso. Continua al siguiente.";
                nuevoEstado = EstadoConversacion.TRAMITE_EN_PROCESO;
                if (nodoSiguienteId != null) {
                    conv.setNodoActualId(nodoSiguienteId);
                }
            }

            conv.setEstado(nuevoEstado);
            conv.setUltimaActividadEn(LocalDateTime.now());
            agregarMensaje(conv, "agente", mensajeDecision, "estado");
            conversacionRepository.save(conv);

            // Notificar via WebSocket y notificacion
            if (conv.getClienteId() != null) {
                notificacionService.crearNotificacion(
                        conv.getClienteId(), tramiteId, nodoSiguienteId,
                        decision, mensajeDecision
                );
            }
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
