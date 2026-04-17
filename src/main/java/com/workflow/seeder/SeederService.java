package com.workflow.seeder;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.ejecucion.model.EjecucionNodo;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.empresa.model.Empresa;
import com.workflow.empresa.repository.EmpresaRepository;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.notificacion.repository.NotificacionRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeederService {
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final PoliticaRepository politicaRepository;
    private final NodoRepository nodoRepository;
    private final TransicionRepository transicionRepository;
    private final FormularioRepository formularioRepository;
    private final TramiteRepository tramiteRepository;
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final NotificacionRepository notificacionRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final String PASSWORD = "123456";

    private String seedDatasetRealista() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre("Servicios Integrales Santa Cruz S.R.L.")
                .logoUrl("https://dummyimage.com/240x80/1f1f12/c0c080&text=SISC+Workflow")
                .activo(true)
                .build());

        Usuario adminGeneral = crearUsuario(
                empresa.getId(),
                "Carla Mendez Rojas",
                "admin.general@seed.local",
                "ADMIN_GENERAL",
                null
        );

        Departamento atencion = crearDepartamento(
                empresa.getId(),
                "Atencion al Cliente",
                "Recepcion, validacion documental y orientacion inicial para tramites de servicios."
        );
        Departamento tecnico = crearDepartamento(
                empresa.getId(),
                "Mesa Tecnica",
                "Validacion tecnica, inspecciones y factibilidad operativa."
        );
        Departamento facturacion = crearDepartamento(
                empresa.getId(),
                "Facturacion y Cobros",
                "Emision de cargos, prorrateos y cierre economico del tramite."
        );
        Departamento legal = crearDepartamento(
                empresa.getId(),
                "Asesoria Legal",
                "Revision de contratos, observaciones y cumplimiento normativo."
        );

        Usuario adminDeptoAtencion = crearUsuario(
                empresa.getId(),
                "Marco Antonio Vargas",
                "admin.depto@seed.local",
                "ADMIN_DEPARTAMENTO",
                atencion.getId()
        );
        Usuario adminDeptoTecnico = crearUsuario(
                empresa.getId(),
                "Sandra Quiroga Salazar",
                "admin.tecnico@seed.local",
                "ADMIN_DEPARTAMENTO",
                tecnico.getId()
        );
        Usuario adminDeptoFact = crearUsuario(
                empresa.getId(),
                "Luis Fernando Rojas",
                "admin.facturacion@seed.local",
                "ADMIN_DEPARTAMENTO",
                facturacion.getId()
        );
        Usuario adminDeptoLegal = crearUsuario(
                empresa.getId(),
                "Gabriela Pinto Rocabado",
                "admin.legal@seed.local",
                "ADMIN_DEPARTAMENTO",
                legal.getId()
        );

        Usuario funcionarioTecnico = crearUsuario(
                empresa.getId(),
                "Jorge Miguel Castro",
                "funcionario@seed.local",
                "FUNCIONARIO",
                tecnico.getId()
        );
        Usuario funcionarioAtencion = crearUsuario(
                empresa.getId(),
                "Paola Andrea Flores",
                "func.atencion@seed.local",
                "FUNCIONARIO",
                atencion.getId()
        );
        Usuario funcionarioFacturacion = crearUsuario(
                empresa.getId(),
                "Diego Ernesto Paz",
                "func.facturacion@seed.local",
                "FUNCIONARIO",
                facturacion.getId()
        );

        atencion.setAdminDepartamentoId(adminDeptoAtencion.getId());
        tecnico.setAdminDepartamentoId(adminDeptoTecnico.getId());
        facturacion.setAdminDepartamentoId(adminDeptoFact.getId());
        legal.setAdminDepartamentoId(adminDeptoLegal.getId());
        departamentoRepository.saveAll(List.of(atencion, tecnico, facturacion, legal));

        Politica politica = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Alta de Servicio Domiciliario")
                .descripcion("Flujo completo de solicitud de alta de servicio con evaluacion tecnica y cierre administrativo.")
                .version(3)
                .estado("BORRADOR")
                .generadaPorIa(false)
                .creadoPor(adminGeneral.getId())
                .activo(true)
                .build());

        Nodo nInicio = crearNodo(politica.getId(), atencion.getId(), "Inicio", "INICIO", 90D, 120D);
        Nodo nRecepcion = crearNodo(politica.getId(), atencion.getId(), "Recepcion de Solicitud", "TAREA", 290D, 120D);
        Nodo nEvaluacion = crearNodo(politica.getId(), tecnico.getId(), "Evaluacion Tecnica", "DECISION", 530D, 260D);
        Nodo nFacturacion = crearNodo(politica.getId(), facturacion.getId(), "Generar Cargo Inicial", "TAREA", 760D, 120D);
        Nodo nLegal = crearNodo(politica.getId(), legal.getId(), "Emitir Observacion Legal", "TAREA", 760D, 410D);
        Nodo nFin = crearNodo(politica.getId(), facturacion.getId(), "Cierre del Tramite", "FIN", 990D, 260D);

        transicionRepository.saveAll(List.of(
                crearTransicion(politica.getId(), nInicio.getId(), nRecepcion.getId(), "LINEAL", "Continuar", null),
                crearTransicion(politica.getId(), nRecepcion.getId(), nEvaluacion.getId(), "LINEAL", "Derivar", null),
                crearTransicion(politica.getId(), nEvaluacion.getId(), nFacturacion.getId(), "ALTERNATIVA", "Aprobado", "[factibilidad=SI]"),
                crearTransicion(politica.getId(), nEvaluacion.getId(), nLegal.getId(), "ALTERNATIVA", "Observado", "[factibilidad=NO]"),
                crearTransicion(politica.getId(), nFacturacion.getId(), nFin.getId(), "LINEAL", "Cerrar", null),
                crearTransicion(politica.getId(), nLegal.getId(), nFin.getId(), "LINEAL", "Cerrar con observacion", null)
        ));

        Formulario formularioRecepcion = formularioRepository.save(Formulario.builder()
                .politicaId(politica.getId())
                .nodoId(nRecepcion.getId())
                .nombre("Ficha de Solicitud de Servicio")
                .generadoPorIa(false)
                .creadoPor(adminDeptoAtencion.getId())
                .activo(true)
                .campos(List.of(
                        campo("numeroSolicitud", "Numero de Solicitud", "TEXTO", true, false, List.of()),
                        campo("cliente", "Nombre del Cliente", "TEXTO", true, false, List.of()),
                        campo("zona", "Zona", "SELECCION", true, false, List.of("Plan 3000", "Equipetrol", "Villa 1ro de Mayo", "Centro")),
                        campo("prioridad", "Prioridad", "SELECCION", true, true, List.of("ALTA", "MEDIA", "BAJA"))
                ))
                .build());

        Formulario formularioDecision = formularioRepository.save(Formulario.builder()
                .politicaId(politica.getId())
                .nodoId(nEvaluacion.getId())
                .nombre("Resultado de Evaluacion Tecnica")
                .generadoPorIa(false)
                .creadoPor(adminDeptoTecnico.getId())
                .activo(true)
                .campos(List.of(
                        campo("factibilidad", "Factibilidad", "SELECCION", true, true, List.of("Aprobado", "Observado")),
                        campo("inspector", "Inspector Asignado", "TEXTO", true, false, List.of()),
                        campo("observacionesTecnicas", "Observaciones Tecnicas", "TEXTO", false, false, List.of())
                ))
                .build());

        nRecepcion.setFormularioId(formularioRecepcion.getId());
        nEvaluacion.setFormularioId(formularioDecision.getId());
        nodoRepository.saveAll(List.of(nRecepcion, nEvaluacion));

        politica.setEstado("ACTIVA");
        politicaRepository.save(politica);

        crearTramitesDemo(empresa, politica, adminGeneral, nRecepcion, tecnico.getId(), funcionarioTecnico.getId(), funcionarioAtencion.getId());

        return """
                Seeder realista aplicado.
                Credenciales:
                - admin.general@seed.local / 123456
                - admin.depto@seed.local / 123456
                - funcionario@seed.local / 123456
                Usuarios extra:
                - func.atencion@seed.local / 123456
                - func.facturacion@seed.local / 123456
                """;
    }

    public String seedAll() {
        this.clearAll();
        return this.seedDatasetRealista();
    }

    public String clearAll() {
        notificacionRepository.deleteAll();
        ejecucionNodoRepository.deleteAll();
        tramiteRepository.deleteAll();
        formularioRepository.deleteAll();
        transicionRepository.deleteAll();
        nodoRepository.deleteAll();
        politicaRepository.deleteAll();
        departamentoRepository.deleteAll();
        usuarioRepository.deleteAll();
        empresaRepository.deleteAll();
        return "Datos eliminados";
    }

    private Usuario crearUsuario(String empresaId, String nombre, String email, String rol, String departamentoId) {
        return usuarioRepository.save(Usuario.builder()
                .empresaId(empresaId)
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol(rol)
                .departamentoId(departamentoId)
                .activo(true)
                .build());
    }

    private Departamento crearDepartamento(String empresaId, String nombre, String descripcion) {
        Departamento d = new Departamento();
        d.setEmpresaId(empresaId);
        d.setNombre(nombre);
        d.setDescripcion(descripcion);
        d.setActivo(true);
        return departamentoRepository.save(d);
    }

    private Nodo crearNodo(String politicaId, String departamentoId, String nombre, String tipo, double x, double y) {
        return nodoRepository.save(Nodo.builder()
                .politicaId(politicaId)
                .departamentoId(departamentoId)
                .nombre(nombre)
                .tipo(tipo)
                .posicionX(x)
                .posicionY(y)
                .activo(true)
                .build());
    }

    private Transicion crearTransicion(String politicaId, String origenId, String destinoId, String tipo, String etiqueta, String condicion) {
        return Transicion.builder()
                .politicaId(politicaId)
                .nodoOrigenId(origenId)
                .nodoDestinoId(destinoId)
                .tipo(tipo)
                .etiqueta(etiqueta)
                .condicion(condicion)
                .activo(true)
                .build();
    }

    private Formulario.CampoFormulario campo(String nombre, String etiqueta, String tipo, boolean requerido, boolean prioridad, List<String> opciones) {
        return Formulario.CampoFormulario.builder()
                .nombre(nombre)
                .etiqueta(etiqueta)
                .tipo(tipo)
                .requerido(requerido)
                .esCampoPrioridad(prioridad)
                .opciones(opciones)
                .build();
    }

    private void crearTramitesDemo(
            Empresa empresa,
            Politica politica,
            Usuario adminGeneral,
            Nodo nodoRecepcion,
            String departamentoTecnicoId,
            String funcionarioTecnicoId,
            String funcionarioAtencionId
    ) {
        List<Tramite> tramites = List.of(
                Tramite.builder()
                        .politicaId(politica.getId())
                        .empresaId(empresa.getId())
                        .titulo("Alta de medidor - Carlos Rojas")
                        .prioridad("ALTA")
                        .estadoGeneral("EN_PROCESO")
                        .nodoActualId(nodoRecepcion.getId())
                        .fechaLimite(LocalDateTime.now().plusDays(2))
                        .iniciadoPor(adminGeneral.getId())
                        .iniciadoEn(LocalDateTime.now().minusHours(8))
                        .build(),
                Tramite.builder()
                        .politicaId(politica.getId())
                        .empresaId(empresa.getId())
                        .titulo("Cambio de titularidad - Ana Vargas")
                        .prioridad("MEDIA")
                        .estadoGeneral("EN_PROCESO")
                        .nodoActualId(nodoRecepcion.getId())
                        .fechaLimite(LocalDateTime.now().plusDays(4))
                        .iniciadoPor(adminGeneral.getId())
                        .iniciadoEn(LocalDateTime.now().minusHours(3))
                        .build(),
                Tramite.builder()
                        .politicaId(politica.getId())
                        .empresaId(empresa.getId())
                        .titulo("Inspeccion por reclamo tecnico - Maria Prado")
                        .prioridad("ALTA")
                        .estadoGeneral("EN_PROCESO")
                        .nodoActualId(nodoRecepcion.getId())
                        .fechaLimite(LocalDateTime.now().plusDays(1))
                        .iniciadoPor(adminGeneral.getId())
                        .iniciadoEn(LocalDateTime.now().minusHours(1))
                        .build()
        );

        List<Tramite> guardados = tramiteRepository.saveAll(tramites);
        ejecucionNodoRepository.saveAll(List.of(
                EjecucionNodo.builder()
                        .tramiteId(guardados.get(0).getId())
                        .nodoId(nodoRecepcion.getId())
                        .departamentoId(nodoRecepcion.getDepartamentoId())
                        .funcionarioId(funcionarioAtencionId)
                        .estado("PENDIENTE")
                        .respuestaFormulario(Map.of("zona", "Plan 3000", "prioridad", "ALTA"))
                        .iniciadoEn(LocalDateTime.now().minusHours(8))
                        .build(),
                EjecucionNodo.builder()
                        .tramiteId(guardados.get(1).getId())
                        .nodoId(nodoRecepcion.getId())
                        .departamentoId(nodoRecepcion.getDepartamentoId())
                        .funcionarioId(funcionarioAtencionId)
                        .estado("EN_PROCESO")
                        .respuestaFormulario(Map.of("zona", "Centro", "prioridad", "MEDIA"))
                        .iniciadoEn(LocalDateTime.now().minusHours(2))
                        .build(),
                EjecucionNodo.builder()
                        .tramiteId(guardados.get(2).getId())
                        .nodoId(nodoRecepcion.getId())
                        .departamentoId(departamentoTecnicoId)
                        .funcionarioId(funcionarioTecnicoId)
                        .estado("PENDIENTE")
                        .respuestaFormulario(Map.of("zona", "Villa 1ro de Mayo", "prioridad", "ALTA"))
                        .iniciadoEn(LocalDateTime.now().minusMinutes(45))
                        .build()
        ));
    }
}
