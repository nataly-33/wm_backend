package com.workflow.config;

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
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final EmpresaRepository empresaRepository;
    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoliticaRepository politicaRepository;
    private final NodoRepository nodoRepository;
    private final TransicionRepository transicionRepository;
    private final FormularioRepository formularioRepository;
    private final TramiteRepository tramiteRepository;
    private final EjecucionNodoRepository ejecucionNodoRepository;
    private final BCryptPasswordEncoder passwordEncoder;
        @Value("${app.seeder.startup-enabled:false}")
        private boolean startupSeederEnabled;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
                        if (!startupSeederEnabled) {
                                log.info("Startup DataSeeder deshabilitado (app.seeder.startup-enabled=false).");
                                return;
                        }
            if (empresaRepository.count() > 0) {
                log.info("Base de datos ya poblada. Se salta el DataSeeder.");
                return;
            }
            log.info("Iniciando DataSeeder CRE Santa Cruz...");
            seedCreSantaCruz();
            log.info("DataSeeder finalizado con exito.");
        };
    }

        public String ejecutarSeedManualSiVacia() {
                if (empresaRepository.count() > 0) {
                        return "La base ya contiene datos. Ejecute DELETE /api/v1/seeder/clear y luego POST /api/v1/seeder/run.";
                }

                log.info("Ejecutando seeder manual CRE Santa Cruz...");
                seedCreSantaCruz();
                return "Seeder CRE Santa Cruz aplicado con exito. Usuario principal: admin@cre.bo / Admin123!";
        }

    private void seedCreSantaCruz() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre("CRE Santa Cruz")
                .activo(true)
                .build());

        Usuario adminGen = usuarioRepository.save(Usuario.builder()
                .empresaId(empresa.getId())
                .nombre("Admin General Seeder")
                .email("admin@cre.bo")
                .passwordHash(passwordEncoder.encode("Admin123!"))
                .rol("ADMIN_GENERAL")
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build());

        Departamento dAtencion = crearDepto(empresa.getId(), "Atención al Cliente", "Atención y orientación al socio.");
        Departamento dTecnico = crearDepto(empresa.getId(), "Técnico", "Inspecciones y operaciones de campo.");
        Departamento dFact = crearDepto(empresa.getId(), "Facturación", "Cobros y cargos.");
        Departamento dLegal = crearDepto(empresa.getId(), "Legal", "Contratos y observaciones legales.");
        Departamento dRrhh = crearDepto(empresa.getId(), "Recursos Humanos", "Personal y escalamiento interno.");

        Usuario admA = crearUsuarioDepto(empresa.getId(), "Admin Atención", "admin.atencion@cre.bo", "Admin123!", dAtencion);
        Usuario admT = crearUsuarioDepto(empresa.getId(), "Admin Técnico", "admin.tecnico@cre.bo", "Admin123!", dTecnico);
        Usuario admF = crearUsuarioDepto(empresa.getId(), "Admin Facturación", "admin.facturacion@cre.bo", "Admin123!", dFact);
        Usuario admL = crearUsuarioDepto(empresa.getId(), "Admin Legal", "admin.legal@cre.bo", "Admin123!", dLegal);
        Usuario admR = crearUsuarioDepto(empresa.getId(), "Admin RRHH", "admin.rrhh@cre.bo", "Admin123!", dRrhh);

        crearFunc(empresa.getId(), "Func 1 Atención", "func1.atencion@cre.bo", dAtencion);
        crearFunc(empresa.getId(), "Func 2 Atención", "func2.atencion@cre.bo", dAtencion);
        crearFunc(empresa.getId(), "Func 1 Técnico", "func1.tecnico@cre.bo", dTecnico);
        crearFunc(empresa.getId(), "Func 2 Técnico", "func2.tecnico@cre.bo", dTecnico);
        crearFunc(empresa.getId(), "Func 1 Facturación", "func1.facturacion@cre.bo", dFact);
        crearFunc(empresa.getId(), "Func 2 Facturación", "func2.facturacion@cre.bo", dFact);
        crearFunc(empresa.getId(), "Func 1 Legal", "func1.legal@cre.bo", dLegal);
        crearFunc(empresa.getId(), "Func 2 Legal", "func2.legal@cre.bo", dLegal);
        crearFunc(empresa.getId(), "Func 1 RRHH", "func1.rrhh@cre.bo", dRrhh);
        crearFunc(empresa.getId(), "Func 2 RRHH", "func2.rrhh@cre.bo", dRrhh);

        seedPolitica1(empresa, adminGen, dAtencion, dTecnico, dFact, dLegal);
        seedPolitica2(empresa, adminGen, dAtencion, dTecnico, dFact);
        seedPolitica3(empresa, adminGen, dAtencion, dTecnico, dFact, dLegal);
        seedPolitica4(empresa, adminGen, dAtencion, dFact, dRrhh);
    }

    private void seedPolitica1(Empresa empresa, Usuario adminGen, Departamento at, Departamento te, Departamento fa, Departamento le) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Instalación de Nuevo Medidor")
                .descripcion("Flujo con decisión documental y rama aprobada multi-departamento.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        Nodo n1 = crearNodo(pol.getId(), at.getId(), "INICIO", "Inicio solicitud", 40, 40);
        Nodo n2 = crearNodo(pol.getId(), at.getId(), "TAREA", "Recibir solicitud del cliente", 40, 120);
        Nodo n3 = crearNodo(pol.getId(), at.getId(), "TAREA", "Verificar documentación", 40, 220);
        Nodo n4 = crearNodo(pol.getId(), at.getId(), "DECISION", "¿Documentación completa?", 40, 320);
        Nodo n5 = crearNodo(pol.getId(), te.getId(), "TAREA", "Realizar inspección técnica", 40, 420);
        Nodo n6 = crearNodo(pol.getId(), te.getId(), "TAREA", "Generar presupuesto", 40, 520);
        Nodo n7 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Registrar pago", 40, 620);
        Nodo n8 = crearNodo(pol.getId(), le.getId(), "TAREA", "Firmar contrato", 40, 720);
        Nodo n9 = crearNodo(pol.getId(), null, "FIN", "Fin instalación", 40, 820);
        Nodo n10 = crearNodo(pol.getId(), at.getId(), "TAREA", "Notificar al cliente", 300, 420);
        Nodo n11 = crearNodo(pol.getId(), null, "FIN", "Fin rechazado", 300, 520);

        formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n2.getId())
                .nombre("Solicitud de medidor")
                .activo(true)
                .campos(List.of(
                        campo("nombre_titular", "Nombre titular", "TEXTO", true, false, null),
                        campo("ci_titular", "CI titular", "TEXTO", true, false, null),
                        campo("direccion_instalacion", "Dirección instalación", "TEXTO", true, false, null),
                        campo("tipo_medidor", "Tipo medidor", "SELECCION", true, false, List.of("Monofásico", "Trifásico")),
                        campo("foto_predio", "Foto predio", "IMAGEN", false, false, null)
                ))
                .build());

        formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n3.getId())
                .nombre("Verificación documental")
                .activo(true)
                .campos(List.of(
                        campo("doc_completa", "Documentación íntegra", "TEXTO", true, false, null)
                ))
                .build());

        formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n4.getId())
                .nombre("Decisión documentación")
                .activo(true)
                .campos(List.of(
                        campo("resultado", "Resultado", "TEXTO", true, true, null)
                ))
                .build());

        crearTransicion(pol.getId(), n1.getId(), n2.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n2.getId(), n3.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n3.getId(), n4.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n4.getId(), n5.getId(), "ALTERNATIVA", "Aprobado", null);
        crearTransicion(pol.getId(), n4.getId(), n10.getId(), "ALTERNATIVA", "Rechazado", null);
        crearTransicion(pol.getId(), n5.getId(), n6.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n6.getId(), n7.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n7.getId(), n8.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n8.getId(), n9.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n10.getId(), n11.getId(), "LINEAL", null, null);

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);

        Tramite tDone = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId())
                .empresaId(empresa.getId())
                .titulo("Instalación medidor - Caso completado")
                .estadoGeneral("COMPLETADO")
                .nodoActualId(n9.getId())
                .iniciadoPor(adminGen.getId())
                .iniciadoEn(LocalDateTime.now().minusDays(5))
                .finalizadoEn(LocalDateTime.now().minusDays(1))
                .build());

        ejecucionNodoRepository.save(EjecucionNodo.builder()
                .tramiteId(tDone.getId())
                .nodoId(n2.getId())
                .departamentoId(at.getId())
                .estado("COMPLETADO")
                .respuestaFormulario(Map.of("ci_titular", "1234567"))
                .completadoEn(LocalDateTime.now().minusDays(4))
                .iniciadoEn(LocalDateTime.now().minusDays(5))
                .build());

        Tramite tProc = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId())
                .empresaId(empresa.getId())
                .titulo("Instalación medidor - En proceso")
                .estadoGeneral("EN_PROCESO")
                .nodoActualId(n3.getId())
                .iniciadoPor(adminGen.getId())
                .iniciadoEn(LocalDateTime.now().minusHours(3))
                .build());

        ejecucionNodoRepository.save(EjecucionNodo.builder()
                .tramiteId(tProc.getId())
                .nodoId(n3.getId())
                .departamentoId(at.getId())
                .estado("PENDIENTE")
                .iniciadoEn(LocalDateTime.now().minusHours(2))
                .build());
    }

    private void seedPolitica2(Empresa empresa, Usuario adminGen, Departamento at, Departamento te, Departamento fa) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Reconexión de Servicio")
                .descripcion("Fork/join en paralelo y decisión final.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        Nodo n1 = crearNodo(pol.getId(), at.getId(), "INICIO", "Inicio", 40, 40);
        Nodo n2 = crearNodo(pol.getId(), at.getId(), "TAREA", "Recibir solicitud de reconexión", 40, 120);
        Nodo nf = crearNodo(pol.getId(), at.getId(), "PARALELO", "Fork paralelo", 40, 220);
        Nodo nA = crearNodo(pol.getId(), fa.getId(), "TAREA", "Verificar deuda pendiente", 200, 220);
        Nodo nB = crearNodo(pol.getId(), te.getId(), "TAREA", "Verificar estado técnico", 360, 220);
        Nodo nj = crearNodo(pol.getId(), at.getId(), "PARALELO", "Join", 200, 360);
        Nodo nd = crearNodo(pol.getId(), at.getId(), "DECISION", "¿Todo aprobado?", 200, 460);
        Nodo nOk = crearNodo(pol.getId(), te.getId(), "TAREA", "Ejecutar reconexión", 40, 560);
        Nodo nKo = crearNodo(pol.getId(), at.getId(), "TAREA", "Informar impedimento", 360, 560);
        Nodo f1 = crearNodo(pol.getId(), null, "FIN", "Fin OK", 40, 660);
        Nodo f2 = crearNodo(pol.getId(), null, "FIN", "Fin rechazo", 360, 660);

        formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(nd.getId())
                .nombre("Decisión reconexión")
                .activo(true)
                .campos(List.of(campo("resultado", "Resultado (Aprobado / Rechazado)", "TEXTO", true, true, null)))
                .build());

        crearTransicion(pol.getId(), n1.getId(), n2.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n2.getId(), nf.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), nf.getId(), nA.getId(), "PARALELA", null, null);
        crearTransicion(pol.getId(), nf.getId(), nB.getId(), "PARALELA", null, null);
        crearTransicion(pol.getId(), nA.getId(), nj.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), nB.getId(), nj.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), nj.getId(), nd.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), nd.getId(), nOk.getId(), "ALTERNATIVA", "Aprobado", null);
        crearTransicion(pol.getId(), nd.getId(), nKo.getId(), "ALTERNATIVA", "Rechazado", null);
        crearTransicion(pol.getId(), nOk.getId(), f1.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), nKo.getId(), f2.getId(), "LINEAL", null, null);

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);

        Tramite t1 = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId())
                .empresaId(empresa.getId())
                .titulo("Reconexión - Completado")
                .estadoGeneral("COMPLETADO")
                .nodoActualId(f1.getId())
                .iniciadoPor(adminGen.getId())
                .iniciadoEn(LocalDateTime.now().minusDays(2))
                .finalizadoEn(LocalDateTime.now().minusDays(1))
                .build());
        ejecucionNodoRepository.save(EjecucionNodo.builder().tramiteId(t1.getId()).nodoId(nOk.getId())
                .departamentoId(te.getId()).estado("COMPLETADO").iniciadoEn(LocalDateTime.now().minusDays(2)).completadoEn(LocalDateTime.now().minusDays(1)).build());

        Tramite t2 = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId())
                .empresaId(empresa.getId())
                .titulo("Reconexión - En proceso")
                .estadoGeneral("EN_PROCESO")
                .nodoActualId(nA.getId())
                .iniciadoPor(adminGen.getId())
                .iniciadoEn(LocalDateTime.now().minusHours(1))
                .build());
        ejecucionNodoRepository.save(EjecucionNodo.builder().tramiteId(t2.getId()).nodoId(nA.getId())
                .departamentoId(fa.getId()).estado("EN_PROCESO").iniciadoEn(LocalDateTime.now().minusMinutes(30)).build());
    }

    private void seedPolitica3(Empresa empresa, Usuario adminGen, Departamento at, Departamento te, Departamento fa, Departamento le) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Baja de Servicio")
                .descripcion("Flujo lineal simple multi-departamento.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        Nodo n1 = crearNodo(pol.getId(), at.getId(), "INICIO", "Inicio", 40, 40);
        Nodo n2 = crearNodo(pol.getId(), at.getId(), "TAREA", "Recibir solicitud de baja", 40, 120);
        Nodo n3 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Verificar saldo cero", 40, 220);
        Nodo n4 = crearNodo(pol.getId(), te.getId(), "TAREA", "Retirar medidor", 40, 320);
        Nodo n5 = crearNodo(pol.getId(), le.getId(), "TAREA", "Liquidar contrato", 40, 420);
        Nodo n6 = crearNodo(pol.getId(), null, "FIN", "Fin", 40, 520);

        formularioRepository.save(Formulario.builder().politicaId(pol.getId()).nodoId(n2.getId()).nombre("Baja").activo(true)
                .campos(List.of(campo("motivo", "Motivo", "TEXTO", true, false, null))).build());

        crearTransicion(pol.getId(), n1.getId(), n2.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n2.getId(), n3.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n3.getId(), n4.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n4.getId(), n5.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n5.getId(), n6.getId(), "LINEAL", null, null);

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);

        Tramite t1 = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId()).empresaId(empresa.getId()).titulo("Baja - Completada")
                .estadoGeneral("COMPLETADO").nodoActualId(n6.getId())
                .iniciadoPor(adminGen.getId()).iniciadoEn(LocalDateTime.now().minusDays(10)).finalizadoEn(LocalDateTime.now().minusDays(9)).build());
        ejecucionNodoRepository.save(EjecucionNodo.builder().tramiteId(t1.getId()).nodoId(n5.getId())
                .departamentoId(le.getId()).estado("COMPLETADO").completadoEn(LocalDateTime.now().minusDays(9)).iniciadoEn(LocalDateTime.now().minusDays(10)).build());

        Tramite t2 = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId()).empresaId(empresa.getId()).titulo("Baja - En trámite")
                .estadoGeneral("EN_PROCESO").nodoActualId(n3.getId())
                .iniciadoPor(adminGen.getId()).iniciadoEn(LocalDateTime.now().minusHours(5)).build());
        ejecucionNodoRepository.save(EjecucionNodo.builder().tramiteId(t2.getId()).nodoId(n3.getId())
                .departamentoId(fa.getId()).estado("PENDIENTE").iniciadoEn(LocalDateTime.now().minusHours(4)).build());
    }

    private void seedPolitica4(Empresa empresa, Usuario adminGen, Departamento at, Departamento fa, Departamento rrhh) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Reclamo por Facturación")
                .descripcion("Flujo con ciclo de revisión.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        Nodo n1 = crearNodo(pol.getId(), at.getId(), "INICIO", "Inicio", 40, 40);
        Nodo n2 = crearNodo(pol.getId(), at.getId(), "TAREA", "Registrar reclamo", 40, 120);
        Nodo n3 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Analizar consumo histórico", 40, 220);
        Nodo n4 = crearNodo(pol.getId(), fa.getId(), "DECISION", "¿Error confirmado?", 40, 320);
        Nodo n5 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Emitir nota de crédito", 40, 420);
        Nodo f1 = crearNodo(pol.getId(), null, "FIN", "Fin nota", 40, 520);
        Nodo n6 = crearNodo(pol.getId(), at.getId(), "TAREA", "Explicar al cliente", 300, 320);
        Nodo n7 = crearNodo(pol.getId(), at.getId(), "DECISION", "¿Cliente acepta?", 300, 420);
        Nodo f2 = crearNodo(pol.getId(), null, "FIN", "Fin aceptación", 300, 520);
        Nodo n8 = crearNodo(pol.getId(), rrhh.getId(), "TAREA", "Elevar a supervisor", 460, 420);

        formularioRepository.save(Formulario.builder().politicaId(pol.getId()).nodoId(n4.getId()).nombre("Error facturación").activo(true)
                .campos(List.of(campo("confirmado", "Confirmado", "TEXTO", true, true, null))).build());
        formularioRepository.save(Formulario.builder().politicaId(pol.getId()).nodoId(n7.getId()).nombre("Aceptación").activo(true)
                .campos(List.of(campo("acepta", "Acepta", "TEXTO", true, true, null))).build());

        crearTransicion(pol.getId(), n1.getId(), n2.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n2.getId(), n3.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n3.getId(), n4.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n4.getId(), n5.getId(), "ALTERNATIVA", "Sí", null);
        crearTransicion(pol.getId(), n4.getId(), n6.getId(), "ALTERNATIVA", "No", null);
        crearTransicion(pol.getId(), n5.getId(), f1.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n6.getId(), n7.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n7.getId(), f2.getId(), "ALTERNATIVA", "Sí", null);
        crearTransicion(pol.getId(), n7.getId(), n8.getId(), "ALTERNATIVA", "No", null);
        crearTransicion(pol.getId(), n8.getId(), n3.getId(), "LINEAL", null, null);

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);

        Tramite t1 = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId()).empresaId(empresa.getId()).titulo("Reclamo - Cerrado con nota")
                .estadoGeneral("COMPLETADO").nodoActualId(f1.getId())
                .iniciadoPor(adminGen.getId()).iniciadoEn(LocalDateTime.now().minusDays(3)).finalizadoEn(LocalDateTime.now().minusDays(2)).build());
        ejecucionNodoRepository.save(EjecucionNodo.builder().tramiteId(t1.getId()).nodoId(n5.getId())
                .departamentoId(fa.getId()).estado("COMPLETADO").completadoEn(LocalDateTime.now().minusDays(2)).iniciadoEn(LocalDateTime.now().minusDays(3)).build());

        Tramite t2 = tramiteRepository.save(Tramite.builder()
                .politicaId(pol.getId()).empresaId(empresa.getId()).titulo("Reclamo - En análisis")
                .estadoGeneral("EN_PROCESO").nodoActualId(n3.getId())
                .iniciadoPor(adminGen.getId()).iniciadoEn(LocalDateTime.now().minusHours(2)).build());
        ejecucionNodoRepository.save(EjecucionNodo.builder().tramiteId(t2.getId()).nodoId(n3.getId())
                .departamentoId(fa.getId()).estado("EN_PROCESO").iniciadoEn(LocalDateTime.now().minusHours(1)).build());
    }

    private Formulario.CampoFormulario campo(String nombre, String etiqueta, String tipo, boolean req, boolean prioridad, List<String> opciones) {
        return Formulario.CampoFormulario.builder()
                .nombre(nombre).etiqueta(etiqueta).tipo(tipo).requerido(req).esCampoPrioridad(prioridad).opciones(opciones)
                .build();
    }

    private Departamento crearDepto(String empresaId, String nombre, String descripcion) {
        Departamento d = new Departamento();
        d.setEmpresaId(empresaId);
        d.setNombre(nombre);
        d.setDescripcion(descripcion);
        d.setActivo(true);
        d.setCreadoEn(LocalDateTime.now());
        return departamentoRepository.save(d);
    }

    private Usuario crearUsuarioDepto(String empId, String nombre, String email, String rawPassword, Departamento depto) {
        Usuario u = usuarioRepository.save(Usuario.builder()
                .empresaId(empId)
                .departamentoId(depto.getId())
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .rol("ADMIN_DEPARTAMENTO")
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build());
        depto.setAdminDepartamentoId(u.getId());
        departamentoRepository.save(depto);
        return u;
    }

    private void crearFunc(String empId, String nombre, String email, Departamento depto) {
        usuarioRepository.save(Usuario.builder()
                .empresaId(empId)
                .departamentoId(depto.getId())
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode("Func123!"))
                .rol("FUNCIONARIO")
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build());
    }

    private Nodo crearNodo(String polId, String deptoId, String tipo, String nombre, double x, double y) {
        return nodoRepository.save(Nodo.builder()
                .politicaId(polId)
                .departamentoId(deptoId)
                .tipo(tipo)
                .nombre(nombre)
                .posicionX(x)
                .posicionY(y)
                .activo(true)
                .build());
    }

    private Transicion crearTransicion(String polId, String from, String to, String tipo, String etiqueta, String condicion) {
        return transicionRepository.save(Transicion.builder()
                .politicaId(polId)
                .nodoOrigenId(from)
                .nodoDestinoId(to)
                .tipo(tipo)
                .etiqueta(etiqueta)
                .condicion(condicion)
                .activo(true)
                .build());
    }
}
