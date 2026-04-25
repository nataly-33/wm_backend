package com.workflow.config;

import com.workflow.departamento.model.Departamento;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.empresa.model.Empresa;
import com.workflow.empresa.repository.EmpresaRepository;
import com.workflow.formulario.model.Formulario;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.model.Nodo;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.politica.model.Politica;
import com.workflow.politica.repository.PoliticaRepository;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

        private static final double LANE_WIDTH = 280d;
        private static final double LANE_START_X = 20d;
        private static final double BASE_Y = 60d;
        private static final double LEVEL_GAP = 160d;
        private static final String FALLBACK_LANE_ID = "__default_lane__";

    private final EmpresaRepository empresaRepository;
    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoliticaRepository politicaRepository;
    private final NodoRepository nodoRepository;
    private final TransicionRepository transicionRepository;
    private final FormularioRepository formularioRepository;
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
        Nodo n5 = crearNodo(pol.getId(), te.getId(), "TAREA", "Inspección técnica", 40, 320);
        Nodo n6 = crearNodo(pol.getId(), te.getId(), "TAREA", "Generar presupuesto", 40, 420);
        Nodo n7 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Registrar pago", 40, 520);
        Nodo n8 = crearNodo(pol.getId(), le.getId(), "TAREA", "Firmar contrato", 40, 620);
        Nodo n9 = crearNodo(pol.getId(), null, "FIN", "Fin instalación", 40, 720);
        Nodo n10 = crearNodo(pol.getId(), at.getId(), "TAREA", "Notificar al cliente", 300, 320);
        Nodo n11 = crearNodo(pol.getId(), null, "FIN", "Fin rechazado", 300, 420);

        // Formulario nodo n2: Recibir solicitud del cliente
        Formulario fN2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n2.getId())
                .nombre("Solicitud de instalación de medidor")
                .activo(true)
                .campos(List.of(
                        campo("nombre_titular", "Nombre completo del titular", "TEXTO", true, false, null),
                        campo("ci_titular", "Cédula de identidad", "TEXTO", true, false, null),
                        campo("telefono", "Teléfono de contacto", "TEXTO", true, false, null),
                        campo("direccion", "Dirección de instalación", "TEXTO", true, false, null),
                        campo("tipo_medidor", "Tipo de medidor solicitado", "SELECCION", true, false, List.of("Monofásico", "Trifásico")),
                        campo("foto_predio", "Foto del predio", "IMAGEN", false, false, null)
                ))
                .build());
        n2.setFormularioId(fN2.getId());
        nodoRepository.save(n2);

        // Formulario nodo n3: Verificar documentación
        Formulario fN3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n3.getId())
                .nombre("Verificación de documentación")
                .activo(true)
                .campos(List.of(
                        campo("documentos_recibidos", "¿Documentos recibidos correctamente?", "SELECCION", true, false, List.of("Completo", "Incompleto")),
                        campo("observaciones_docs", "Observaciones", "TEXTO", false, false, null)
                ))
                .build());
        n3.setFormularioId(fN3.getId());
        nodoRepository.save(n3);

        // Formulario nodo n4: DECISION ¿Documentación completa?
        Formulario fN4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n4.getId())
                .nombre("Decisión de documentación")
                .activo(true)
                .campos(List.of(
                        campo("resultado", "Resultado de la verificación", "SELECCION", true, true, List.of("Aprobado", "Rechazado"))
                ))
                .build());
        n4.setFormularioId(fN4.getId());
        nodoRepository.save(n4);

        // Formulario nodo n5: Inspección técnica
        Formulario fN5 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n5.getId())
                .nombre("Inspección técnica")
                .activo(true)
                .campos(List.of(
                        campo("resultado_inspeccion", "Resultado de la inspección", "SELECCION", true, false, List.of("Viable", "No viable")),
                        campo("observaciones", "Observaciones técnicas", "TEXTO", false, false, null),
                        campo("foto_medidor", "Foto del punto de instalación", "IMAGEN", false, false, null)
                ))
                .build());
        n5.setFormularioId(fN5.getId());
        nodoRepository.save(n5);

        // Formulario nodo n6: Generar presupuesto
        Formulario fN6 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n6.getId())
                .nombre("Presupuesto de instalación")
                .activo(true)
                .campos(List.of(
                        campo("monto_presupuesto", "Monto del presupuesto (Bs)", "NUMERO", true, false, null),
                        campo("tiempo_estimado", "Tiempo estimado de instalación", "TEXTO", true, false, null)
                ))
                .build());
        n6.setFormularioId(fN6.getId());
        nodoRepository.save(n6);

        // Formulario nodo n7: Registrar pago
        Formulario fN7 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n7.getId())
                .nombre("Registro de pago")
                .activo(true)
                .campos(List.of(
                        campo("numero_recibo", "Número de recibo de pago", "TEXTO", true, false, null),
                        campo("monto_pagado", "Monto pagado (Bs)", "NUMERO", true, false, null),
                        campo("fecha_pago", "Fecha de pago", "FECHA", true, false, null)
                ))
                .build());
        n7.setFormularioId(fN7.getId());
        nodoRepository.save(n7);

        // Formulario nodo n8: Firmar contrato
        Formulario fN8 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n8.getId())
                .nombre("Firma de contrato")
                .activo(true)
                .campos(List.of(
                        campo("numero_contrato", "Número de contrato", "TEXTO", true, false, null),
                        campo("doc_contrato", "Documento del contrato firmado", "ARCHIVO", true, false, null)
                ))
                .build());
        n8.setFormularioId(fN8.getId());
        nodoRepository.save(n8);

        // Formulario nodo n10: Notificar al cliente
        Formulario fN10 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId())
                .nodoId(n10.getId())
                .nombre("Notificación al cliente")
                .activo(true)
                .campos(List.of(
                        campo("motivo_rechazo", "Motivo del rechazo", "TEXTO", true, false, null),
                        campo("fecha_reintento", "Fecha para reintentar", "FECHA", false, false, null)
                ))
                .build());
        n10.setFormularioId(fN10.getId());
        nodoRepository.save(n10);

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

        aplicarLayoutSeeder(
                List.of(n1, n2, n3, n4, n5, n6, n7, n8, n9, n10, n11),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, te, fa, le)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
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
        Nodo nd = crearNodo(pol.getId(), te.getId(), "DECISION", "¿Todo aprobado?", 200, 460);
        Nodo nOk = crearNodo(pol.getId(), te.getId(), "TAREA", "Ejecutar reconexión", 40, 560);
        Nodo nKo = crearNodo(pol.getId(), at.getId(), "TAREA", "Informar impedimento", 360, 560);
        Nodo f1 = crearNodo(pol.getId(), null, "FIN", "Fin OK", 40, 660);
        Nodo f2 = crearNodo(pol.getId(), null, "FIN", "Fin rechazo", 360, 660);

        // Formulario nodo n2: Recibir solicitud de reconexión
        Formulario fP2N2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n2.getId()).nombre("Solicitud de reconexión").activo(true)
                .campos(List.of(
                        campo("nombre_cliente", "Nombre del cliente", "TEXTO", true, false, null),
                        campo("numero_cuenta", "Número de cuenta del servicio", "TEXTO", true, false, null),
                        campo("motivo_corte", "Motivo del corte previo", "TEXTO", false, false, null)
                )).build());
        n2.setFormularioId(fP2N2.getId()); nodoRepository.save(n2);

        // Formulario nodo nA: Verificar deuda pendiente
        Formulario fP2NA = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nA.getId()).nombre("Verificación de deuda").activo(true)
                .campos(List.of(
                        campo("monto_deuda", "Monto de deuda pendiente (Bs)", "NUMERO", true, false, null),
                        campo("estado_deuda", "Estado de la deuda", "SELECCION", true, false, List.of("Sin deuda", "Con deuda", "Deuda pagada hoy"))
                )).build());
        nA.setFormularioId(fP2NA.getId()); nodoRepository.save(nA);

        // Formulario nodo nB: Verificar estado técnico
        Formulario fP2NB = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nB.getId()).nombre("Verificación técnica").activo(true)
                .campos(List.of(
                        campo("estado_instalacion", "Estado de la instalación", "SELECCION", true, false, List.of("Apto", "Requiere reparación", "Inhabilitado")),
                        campo("observaciones", "Observaciones técnicas", "TEXTO", false, false, null)
                )).build());
        nB.setFormularioId(fP2NB.getId()); nodoRepository.save(nB);

        // Formulario nodo nd: DECISION ¿Todo aprobado?
        Formulario fP2Nd = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nd.getId()).nombre("Decisión de reconexión").activo(true)
                .campos(List.of(
                        campo("resultado_final", "Resultado final de la evaluación", "SELECCION", true, true, List.of("Aprobado", "Rechazado"))
                )).build());
        nd.setFormularioId(fP2Nd.getId()); nodoRepository.save(nd);

        // Formulario nodo nOk: Ejecutar reconexión
        Formulario fP2NOk = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nOk.getId()).nombre("Ejecución de reconexión").activo(true)
                .campos(List.of(
                        campo("fecha_reconexion", "Fecha de reconexión", "FECHA", true, false, null),
                        campo("tecnico_ejecutor", "Técnico que ejecutó", "TEXTO", true, false, null),
                        campo("foto_reconexion", "Foto de la reconexión", "IMAGEN", false, false, null)
                )).build());
        nOk.setFormularioId(fP2NOk.getId()); nodoRepository.save(nOk);

        // Formulario nodo nKo: Informar impedimento
        Formulario fP2NKo = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nKo.getId()).nombre("Informe de impedimento").activo(true)
                .campos(List.of(
                        campo("motivo_impedimento", "Motivo del impedimento", "TEXTO", true, false, null)
                )).build());
        nKo.setFormularioId(fP2NKo.getId()); nodoRepository.save(nKo);

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

        aplicarLayoutSeeder(
                List.of(n1, n2, nf, nA, nB, nj, nd, nOk, nKo, f1, f2),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, te, fa)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
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
        Nodo n3 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Verificar saldo cero", 40, 120);
        Nodo n4 = crearNodo(pol.getId(), te.getId(), "TAREA", "Retirar medidor", 40, 220);
        Nodo n5 = crearNodo(pol.getId(), le.getId(), "TAREA", "Liquidar contrato", 40, 320);
        Nodo n6 = crearNodo(pol.getId(), null, "FIN", "Fin", 40, 420);

        // Formulario nodo n2: Recibir solicitud de baja
        Formulario fP3N2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n2.getId()).nombre("Solicitud de baja de servicio").activo(true)
                .campos(List.of(
                        campo("nombre_titular", "Nombre del titular", "TEXTO", true, false, null),
                        campo("numero_cuenta", "Número de cuenta", "TEXTO", true, false, null),
                        campo("motivo_baja", "Motivo de la baja", "SELECCION", true, false, List.of("Mudanza", "Fallecimiento", "Económico", "Otro"))
                )).build());
        n2.setFormularioId(fP3N2.getId()); nodoRepository.save(n2);

        // Formulario nodo n3: Verificar saldo cero
        Formulario fP3N3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n3.getId()).nombre("Verificación de saldo").activo(true)
                .campos(List.of(
                        campo("saldo_pendiente", "Saldo pendiente (Bs)", "NUMERO", true, false, null),
                        campo("estado_saldo", "Estado del saldo", "SELECCION", true, false, List.of("Saldo cero", "Con deuda"))
                )).build());
        n3.setFormularioId(fP3N3.getId()); nodoRepository.save(n3);

        // Formulario nodo n4: Retirar medidor
        Formulario fP3N4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n4.getId()).nombre("Retiro de medidor").activo(true)
                .campos(List.of(
                        campo("numero_medidor", "Número de serie del medidor", "TEXTO", true, false, null),
                        campo("estado_medidor", "Estado del medidor al retiro", "SELECCION", true, false, List.of("Bueno", "Deteriorado", "Robado")),
                        campo("foto_retiro", "Foto del retiro", "IMAGEN", false, false, null)
                )).build());
        n4.setFormularioId(fP3N4.getId()); nodoRepository.save(n4);

        // Formulario nodo n5: Liquidar contrato
        Formulario fP3N5 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n5.getId()).nombre("Liquidación de contrato").activo(true)
                .campos(List.of(
                        campo("numero_contrato", "Número de contrato liquidado", "TEXTO", true, false, null),
                        campo("monto_liquidado", "Monto final liquidado (Bs)", "NUMERO", true, false, null),
                        campo("doc_liquidacion", "Documento de liquidación", "ARCHIVO", true, false, null)
                )).build());
        n5.setFormularioId(fP3N5.getId()); nodoRepository.save(n5);

        crearTransicion(pol.getId(), n1.getId(), n2.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n2.getId(), n3.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n3.getId(), n4.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n4.getId(), n5.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n5.getId(), n6.getId(), "LINEAL", null, null);

        aplicarLayoutSeeder(
                List.of(n1, n2, n3, n4, n5, n6),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, te, fa, le)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
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
        Nodo n3 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Analizar consumo histórico", 40, 120);
        Nodo n4 = crearNodo(pol.getId(), fa.getId(), "DECISION", "¿Error confirmado?", 40, 220);
        Nodo n5 = crearNodo(pol.getId(), fa.getId(), "TAREA", "Emitir nota de crédito", 40, 320);
        Nodo f1 = crearNodo(pol.getId(), null, "FIN", "Fin nota", 40, 420);
        Nodo n6 = crearNodo(pol.getId(), at.getId(), "TAREA", "Explicar al cliente", 300, 220);
        Nodo n7 = crearNodo(pol.getId(), at.getId(), "DECISION", "¿Cliente acepta?", 300, 320);
        Nodo f2 = crearNodo(pol.getId(), null, "FIN", "Fin aceptación", 300, 420);
        Nodo n8 = crearNodo(pol.getId(), rrhh.getId(), "TAREA", "Elevar a supervisor", 460, 320);

        // Formulario nodo n2: Registrar reclamo
        Formulario fP4N2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n2.getId()).nombre("Registro de reclamo").activo(true)
                .campos(List.of(
                        campo("nombre_reclamante", "Nombre del reclamante", "TEXTO", true, false, null),
                        campo("numero_cuenta", "Número de cuenta", "TEXTO", true, false, null),
                        campo("periodo_reclamo", "Período reclamado (mes/año)", "TEXTO", true, false, null),
                        campo("monto_reclamado", "Monto reclamado (Bs)", "NUMERO", true, false, null),
                        campo("descripcion", "Descripción del reclamo", "TEXTO", true, false, null)
                )).build());
        n2.setFormularioId(fP4N2.getId()); nodoRepository.save(n2);

        // Formulario nodo n3: Analizar consumo histórico
        Formulario fP4N3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n3.getId()).nombre("Análisis de consumo").activo(true)
                .campos(List.of(
                        campo("consumo_promedio", "Consumo promedio últimos 6 meses (kWh)", "NUMERO", true, false, null),
                        campo("consumo_reclamado", "Consumo del período reclamado (kWh)", "NUMERO", true, false, null),
                        campo("diferencia", "Diferencia detectada (%)", "NUMERO", true, false, null)
                )).build());
        n3.setFormularioId(fP4N3.getId()); nodoRepository.save(n3);

        // Formulario nodo n4: DECISION ¿Error confirmado?
        Formulario fP4N4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n4.getId()).nombre("Confirmación de error").activo(true)
                .campos(List.of(
                        campo("resultado", "¿Se confirmó el error en la facturación?", "SELECCION", true, true, List.of("Aprobado", "Rechazado"))
                )).build());
        n4.setFormularioId(fP4N4.getId()); nodoRepository.save(n4);

        // Formulario nodo n5: Emitir nota de crédito
        Formulario fP4N5 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n5.getId()).nombre("Nota de crédito").activo(true)
                .campos(List.of(
                        campo("numero_nota", "Número de nota de crédito", "TEXTO", true, false, null),
                        campo("monto_credito", "Monto a acreditar (Bs)", "NUMERO", true, false, null)
                )).build());
        n5.setFormularioId(fP4N5.getId()); nodoRepository.save(n5);

        // Formulario nodo n6: Explicar al cliente
        Formulario fP4N6 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n6.getId()).nombre("Explicación al cliente").activo(true)
                .campos(List.of(
                        campo("explicacion", "Explicación brindada al cliente", "TEXTO", true, false, null),
                        campo("cliente_acepta", "¿El cliente aceptó la explicación?", "SELECCION", true, true, List.of("Aprobado", "Rechazado"))
                )).build());
        n6.setFormularioId(fP4N6.getId()); nodoRepository.save(n6);

        // Formulario nodo n7: DECISION ¿Cliente acepta?
        Formulario fP4N7 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n7.getId()).nombre("Decisión de aceptación").activo(true)
                .campos(List.of(
                        campo("resultado_aceptacion", "¿El cliente acepta la resolución?", "SELECCION", true, true, List.of("Aprobado", "Rechazado"))
                )).build());
        n7.setFormularioId(fP4N7.getId()); nodoRepository.save(n7);

        // Formulario nodo n8: Elevar a supervisor
        Formulario fP4N8 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(n8.getId()).nombre("Escalamiento a supervisor").activo(true)
                .campos(List.of(
                        campo("supervisor_asignado", "Supervisor asignado", "TEXTO", true, false, null),
                        campo("observaciones", "Observaciones del supervisor", "TEXTO", false, false, null)
                )).build());
        n8.setFormularioId(fP4N8.getId()); nodoRepository.save(n8);

        crearTransicion(pol.getId(), n1.getId(), n2.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n2.getId(), n3.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n3.getId(), n4.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n4.getId(), n5.getId(), "ALTERNATIVA", "Aprobado", null);
        crearTransicion(pol.getId(), n4.getId(), n6.getId(), "ALTERNATIVA", "Rechazado", null);
        crearTransicion(pol.getId(), n5.getId(), f1.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n6.getId(), n7.getId(), "LINEAL", null, null);
        crearTransicion(pol.getId(), n7.getId(), f2.getId(), "ALTERNATIVA", "Aprobado", null);
        crearTransicion(pol.getId(), n7.getId(), n8.getId(), "ALTERNATIVA", "Rechazado", null);
        crearTransicion(pol.getId(), n8.getId(), n3.getId(), "LINEAL", null, null);

        aplicarLayoutSeeder(
                List.of(n1, n2, n3, n4, n5, f1, n6, n7, f2, n8),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, fa, rrhh)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
    }

        private void aplicarLayoutSeeder(List<Nodo> nodos, List<Transicion> transiciones, List<Departamento> carriles) {
                if (nodos == null || nodos.isEmpty()) {
                        return;
                }

                Map<String, Nodo> nodeById = new LinkedHashMap<>();
                for (Nodo nodo : nodos) {
                        nodeById.put(nodo.getId(), nodo);
                }

                Map<String, List<String>> successors = new HashMap<>();
                Map<String, List<String>> predecessors = new HashMap<>();
                for (String nodeId : nodeById.keySet()) {
                        successors.put(nodeId, new ArrayList<>());
                        predecessors.put(nodeId, new ArrayList<>());
                }

                List<Transicion> trList = transiciones == null ? List.of() : transiciones;
                for (Transicion tr : trList) {
                        String from = tr.getNodoOrigenId();
                        String to = tr.getNodoDestinoId();
                        if (!nodeById.containsKey(from) || !nodeById.containsKey(to)) {
                                continue;
                        }
                        if (from.equals(to)) {
                                continue;
                        }
                        successors.get(from).add(to);
                        predecessors.get(to).add(from);
                }

                String inicioId = null;
                for (Nodo nodo : nodos) {
                        if ("INICIO".equals(nodo.getTipo())) {
                                inicioId = nodo.getId();
                                break;
                        }
                }
                if (inicioId == null) {
                        inicioId = nodos.get(0).getId();
                }

                Map<String, Integer> deptFreq = new LinkedHashMap<>();
                for (Nodo nodo : nodos) {
                        String dept = normalizarDepto(nodo.getDepartamentoId());
                        if (dept == null) {
                                continue;
                        }
                        deptFreq.put(dept, deptFreq.getOrDefault(dept, 0) + 1);
                }

                String deptoMasFrecuente = null;
                int maxFreq = -1;
                for (Map.Entry<String, Integer> entry : deptFreq.entrySet()) {
                        if (entry.getValue() > maxFreq) {
                                maxFreq = entry.getValue();
                                deptoMasFrecuente = entry.getKey();
                        }
                }

                List<String> laneOrder = new ArrayList<>();
                Set<String> seenLane = new HashSet<>();

                if (inicioId != null) {
                        Queue<String> queue = new ArrayDeque<>();
                        Set<String> visited = new HashSet<>();
                        queue.add(inicioId);

                        while (!queue.isEmpty()) {
                                String current = queue.poll();
                                if (current == null || !visited.add(current)) {
                                        continue;
                                }

                                String dept = normalizarDepto(nodeById.get(current).getDepartamentoId());
                                if (dept != null && seenLane.add(dept)) {
                                        laneOrder.add(dept);
                                }

                                for (String next : successors.getOrDefault(current, List.of())) {
                                        if (!visited.contains(next)) {
                                                queue.add(next);
                                        }
                                }
                        }
                }

                for (Nodo nodo : nodos) {
                        String dept = normalizarDepto(nodo.getDepartamentoId());
                        if (dept != null && seenLane.add(dept)) {
                                laneOrder.add(dept);
                        }
                }

                Map<String, String> resolvedDeptByNode = new HashMap<>();
                for (String nodeId : nodeById.keySet()) {
                        String dept = resolverDeptoNodo(
                                        nodeId,
                                        nodeById,
                                        successors,
                                        predecessors,
                                        deptoMasFrecuente,
                                        laneOrder,
                                        carriles
                        );
                        resolvedDeptByNode.put(nodeId, dept);
                        if (dept != null && seenLane.add(dept)) {
                                laneOrder.add(dept);
                        }
                }

                if (laneOrder.isEmpty()) {
                        String fallbackCarril = null;
                        if (carriles != null) {
                                for (Departamento carril : carriles) {
                                        String dept = normalizarDepto(carril.getId());
                                        if (dept != null) {
                                                fallbackCarril = dept;
                                                break;
                                        }
                                }
                        }
                        laneOrder.add(fallbackCarril != null ? fallbackCarril : FALLBACK_LANE_ID);
                }

                Map<String, Integer> laneIndexByDept = new HashMap<>();
                for (int i = 0; i < laneOrder.size(); i++) {
                        laneIndexByDept.put(laneOrder.get(i), i);
                }

                List<String> topoOrder = construirOrdenTopologico(new ArrayList<>(nodeById.keySet()), successors, predecessors, inicioId);
                Map<String, Integer> topoIndex = new HashMap<>();
                for (int i = 0; i < topoOrder.size(); i++) {
                        topoIndex.put(topoOrder.get(i), i);
                }

                Map<String, Integer> levels = new HashMap<>();
                if (inicioId != null) {
                        levels.put(inicioId, 0);
                }

                for (String nodeId : topoOrder) {
                        Integer idx = topoIndex.get(nodeId);
                        if (idx == null) {
                                continue;
                        }

                        int level = levels.getOrDefault(nodeId, 0);
                        for (String predId : predecessors.getOrDefault(nodeId, List.of())) {
                                Integer predIdx = topoIndex.get(predId);
                                if (predIdx == null || predIdx >= idx) {
                                        continue;
                                }
                                level = Math.max(level, levels.getOrDefault(predId, 0) + 1);
                        }

                        if (nodeId.equals(inicioId)) {
                                level = 0;
                        }

                        levels.put(nodeId, level);
                }

                alinearNivelesDeRamas(levels, topoIndex, successors, Math.max(1, nodeById.size() * 3));

                for (Nodo nodo : nodos) {
                        String nodeId = nodo.getId();
                        String laneId = resolvedDeptByNode.getOrDefault(nodeId, laneOrder.get(0));
                        int laneIndex = laneIndexByDept.getOrDefault(laneId, 0);

                        double nodeWidth = anchoNodoTipo(nodo.getTipo());
                        int spanFromLane = laneIndex;
                        int spanToLane = laneIndex;

                        if (esTipoParalelo(nodo.getTipo())) {
                                Set<Integer> laneCandidates = new HashSet<>();
                                laneCandidates.add(laneIndex);

                                for (String nextId : successors.getOrDefault(nodeId, List.of())) {
                                        String nextDept = resolvedDeptByNode.getOrDefault(nextId, laneId);
                                        laneCandidates.add(laneIndexByDept.getOrDefault(nextDept, laneIndex));
                                }
                                for (String prevId : predecessors.getOrDefault(nodeId, List.of())) {
                                        String prevDept = resolvedDeptByNode.getOrDefault(prevId, laneId);
                                        laneCandidates.add(laneIndexByDept.getOrDefault(prevDept, laneIndex));
                                }

                                for (Integer candidate : laneCandidates) {
                                        spanFromLane = Math.min(spanFromLane, candidate);
                                        spanToLane = Math.max(spanToLane, candidate);
                                }

                                if (spanToLane > spanFromLane) {
                                        nodeWidth = nodeWidth + ((spanToLane - spanFromLane) * LANE_WIDTH);
                                }
                        }

                        double x = LANE_START_X + (laneIndex * LANE_WIDTH) + ((LANE_WIDTH - nodeWidth) / 2d);
                        if (esTipoParalelo(nodo.getTipo())) {
                                x = LANE_START_X + (spanFromLane * LANE_WIDTH) + 40d;
                        }

                        int level = levels.getOrDefault(nodeId, 0);
                        double y = BASE_Y + (level * LEVEL_GAP);

                        nodo.setPosicionX(x);
                        nodo.setPosicionY(y);

                        if (requiereDepartamento(nodo.getTipo())) {
                                nodo.setDepartamentoId(laneId);
                        }
                }

                nodoRepository.saveAll(nodos);
        }

        private String resolverDeptoNodo(
                        String nodeId,
                        Map<String, Nodo> nodeById,
                        Map<String, List<String>> successors,
                        Map<String, List<String>> predecessors,
                        String deptoMasFrecuente,
                        List<String> laneOrder,
                        List<Departamento> carriles
        ) {
                Nodo nodo = nodeById.get(nodeId);
                if (nodo == null) {
                        return laneOrder.isEmpty() ? FALLBACK_LANE_ID : laneOrder.get(0);
                }

                String dept = normalizarDepto(nodo.getDepartamentoId());
                if (dept != null) {
                        return dept;
                }

                if ("INICIO".equals(nodo.getTipo())) {
                        dept = buscarDeptoMasCercano(nodeId, successors, nodeById);
                } else if ("FIN".equals(nodo.getTipo())) {
                        dept = buscarDeptoMasCercano(nodeId, predecessors, nodeById);
                } else {
                        dept = buscarDeptoMasCercano(nodeId, successors, nodeById);
                        if (dept == null) {
                                dept = buscarDeptoMasCercano(nodeId, predecessors, nodeById);
                        }
                }

                if (dept == null) {
                        dept = deptoMasFrecuente;
                }
                if (dept == null && !laneOrder.isEmpty()) {
                        dept = laneOrder.get(0);
                }
                if (dept == null && carriles != null) {
                        for (Departamento carril : carriles) {
                                String carrilId = normalizarDepto(carril.getId());
                                if (carrilId != null) {
                                        dept = carrilId;
                                        break;
                                }
                        }
                }

                return dept != null ? dept : FALLBACK_LANE_ID;
        }

        private String buscarDeptoMasCercano(
                        String startNodeId,
                        Map<String, List<String>> adjacency,
                        Map<String, Nodo> nodeById
        ) {
                Queue<String> queue = new ArrayDeque<>();
                Set<String> visited = new HashSet<>();
                queue.add(startNodeId);
                visited.add(startNodeId);

                while (!queue.isEmpty()) {
                        String current = queue.poll();
                        if (current == null) {
                                continue;
                        }

                        for (String nextId : adjacency.getOrDefault(current, List.of())) {
                                if (!visited.add(nextId)) {
                                        continue;
                                }

                                Nodo next = nodeById.get(nextId);
                                if (next != null) {
                                        String dept = normalizarDepto(next.getDepartamentoId());
                                        if (dept != null) {
                                                return dept;
                                        }
                                }

                                queue.add(nextId);
                        }
                }

                return null;
        }

        private List<String> construirOrdenTopologico(
                        List<String> nodeIds,
                        Map<String, List<String>> successors,
                        Map<String, List<String>> predecessors,
                        String inicioId
        ) {
                Map<String, Integer> indegree = new HashMap<>();
                for (String nodeId : nodeIds) {
                        indegree.put(nodeId, predecessors.getOrDefault(nodeId, List.of()).size());
                }

                Queue<String> queue = new ArrayDeque<>();
                Set<String> queued = new HashSet<>();
                if (inicioId != null && indegree.getOrDefault(inicioId, 0) == 0) {
                        queue.add(inicioId);
                        queued.add(inicioId);
                }

                for (String nodeId : nodeIds) {
                        if (indegree.getOrDefault(nodeId, 0) == 0 && queued.add(nodeId)) {
                                queue.add(nodeId);
                        }
                }

                List<String> order = new ArrayList<>();
                Set<String> processed = new HashSet<>();

                while (!queue.isEmpty()) {
                        String current = queue.poll();
                        if (current == null || !processed.add(current)) {
                                continue;
                        }
                        order.add(current);

                        for (String nextId : successors.getOrDefault(current, List.of())) {
                                int nextIn = indegree.getOrDefault(nextId, 0) - 1;
                                indegree.put(nextId, nextIn);
                                if (nextIn <= 0 && !processed.contains(nextId) && queued.add(nextId)) {
                                        queue.add(nextId);
                                }
                        }
                }

                Set<String> remaining = new HashSet<>(nodeIds);
                remaining.removeAll(processed);

                if (!remaining.isEmpty() && inicioId != null) {
                        Queue<String> bfsQueue = new ArrayDeque<>();
                        Set<String> bfsSeen = new HashSet<>();
                        bfsQueue.add(inicioId);
                        bfsSeen.add(inicioId);

                        while (!bfsQueue.isEmpty()) {
                                String current = bfsQueue.poll();
                                if (current == null) {
                                        continue;
                                }

                                if (remaining.remove(current)) {
                                        order.add(current);
                                }

                                for (String nextId : successors.getOrDefault(current, List.of())) {
                                        if (bfsSeen.add(nextId)) {
                                                bfsQueue.add(nextId);
                                        }
                                }
                        }
                }

                for (String nodeId : nodeIds) {
                        if (remaining.remove(nodeId)) {
                                order.add(nodeId);
                        }
                }

                return order;
        }

        private void alinearNivelesDeRamas(
                        Map<String, Integer> levels,
                        Map<String, Integer> topoIndex,
                        Map<String, List<String>> successors,
                        int maxIterations
        ) {
                boolean changed = true;
                int guard = 0;

                while (changed && guard < maxIterations) {
                        changed = false;
                        guard++;

                        for (Map.Entry<String, List<String>> entry : successors.entrySet()) {
                                String nodeId = entry.getKey();
                                Integer srcIndex = topoIndex.get(nodeId);
                                if (srcIndex == null) {
                                        continue;
                                }

                                List<String> forward = new ArrayList<>();
                                for (String nextId : entry.getValue()) {
                                        Integer nextIdx = topoIndex.get(nextId);
                                        if (nextIdx != null && srcIndex < nextIdx) {
                                                forward.add(nextId);
                                        }
                                }

                                if (forward.size() < 2) {
                                        continue;
                                }

                                int branchLevel = 0;
                                for (String nextId : forward) {
                                        branchLevel = Math.max(branchLevel, levels.getOrDefault(nextId, 0));
                                }

                                for (String nextId : forward) {
                                        if (levels.getOrDefault(nextId, 0) != branchLevel) {
                                                levels.put(nextId, branchLevel);
                                                changed = true;
                                        }
                                }
                        }

                        for (Map.Entry<String, List<String>> entry : successors.entrySet()) {
                                String srcId = entry.getKey();
                                Integer srcIndex = topoIndex.get(srcId);
                                if (srcIndex == null) {
                                        continue;
                                }

                                int srcLevel = levels.getOrDefault(srcId, 0);
                                for (String nextId : entry.getValue()) {
                                        Integer nextIdx = topoIndex.get(nextId);
                                        if (nextIdx == null || srcIndex >= nextIdx) {
                                                continue;
                                        }
                                        int candidate = srcLevel + 1;
                                        if (levels.getOrDefault(nextId, 0) < candidate) {
                                                levels.put(nextId, candidate);
                                                changed = true;
                                        }
                                }
                        }
                }
        }

        private boolean esTipoParalelo(String tipo) {
                return "PARALELO".equals(tipo) || "PARALELO_FORK".equals(tipo) || "PARALELO_JOIN".equals(tipo);
        }

        private boolean requiereDepartamento(String tipo) {
                return !"INICIO".equals(tipo) && !"FIN".equals(tipo);
        }

        private double anchoNodoTipo(String tipo) {
                if ("INICIO".equals(tipo) || "FIN".equals(tipo)) {
                        return 50d;
                }
                if ("DECISION".equals(tipo)) {
                        return 100d;
                }
                if (esTipoParalelo(tipo)) {
                        return 200d;
                }
                return 160d;
        }

        private String normalizarDepto(String departamentoId) {
                if (departamentoId == null) {
                        return null;
                }
                String clean = departamentoId.trim();
                return clean.isEmpty() ? null : clean;
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
