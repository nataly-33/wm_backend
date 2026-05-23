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
            log.info("Iniciando DataSeeder CRE Bolivia...");
            sembrarDatos();
            log.info("DataSeeder CRE Bolivia finalizado con exito.");
        };
    }

    public String ejecutarSeedManualSiVacia() {
        if (empresaRepository.count() > 0) {
            return "La base ya contiene datos. Ejecute DELETE /api/v1/seeder/clear y luego POST /api/v1/seeder/run.";
        }
        log.info("Ejecutando seeder manual CRE Bolivia...");
        sembrarDatos();
        return "Seeder CRE Bolivia aplicado con exito. Usuario principal: admin@cre.com.bo / Admin123!";
    }

    // =========================================================
    // METODO PRINCIPAL
    // =========================================================

    private void sembrarDatos() {
        Empresa empresa = crearEmpresaCRE();

        // --- Departamentos ---
        Departamento dAtencion = crearDepto(empresa.getId(), "Atención al Cliente", "Atención y orientación al socio CRE.");
        Departamento dTecnico  = crearDepto(empresa.getId(), "Departamento Técnico", "Inspecciones y operaciones de campo.");
        Departamento dFact     = crearDepto(empresa.getId(), "Facturación", "Cobros, pagos y cargos al socio.");
        Departamento dLegal    = crearDepto(empresa.getId(), "Departamento Legal", "Contratos y observaciones legales.");
        Departamento dRrhh     = crearDepto(empresa.getId(), "Recursos Humanos", "Personal y escalamiento interno.");

        // --- Admin General ---
        crearAdmin(empresa.getId(),
                "Jorge Martínez", "admin@cre.com.bo", "Admin123!");

        // --- Admins por departamento ---
        crearAdminDepto(empresa.getId(), "María Fernández",
                "maria.fernandez@cre.com.bo", "Admin123!", dAtencion);
        crearAdminDepto(empresa.getId(), "Luis Pedraza",
                "luis.pedraza@cre.com.bo", "Admin123!", dTecnico);
        crearAdminDepto(empresa.getId(), "Carmen Jordán",
                "carmen.jordan@cre.com.bo", "Admin123!", dFact);
        crearAdminDepto(empresa.getId(), "Patricia Vásquez",
                "patricia.vasquez@cre.com.bo", "Admin123!", dLegal);

        // --- Funcionarios ---
        crearFuncionario(empresa.getId(), "Carlos Vaca",
                "carlos.vaca@cre.com.bo", dAtencion);
        crearFuncionario(empresa.getId(), "Ana Romero",
                "ana.romero@cre.com.bo", dAtencion);
        crearFuncionario(empresa.getId(), "Roberto Suárez",
                "roberto.suarez@cre.com.bo", dTecnico);
        crearFuncionario(empresa.getId(), "Diego Montero",
                "diego.montero@cre.com.bo", dFact);
        crearFuncionario(empresa.getId(), "Fernando Castro",
                "fernando.castro@cre.com.bo", dLegal);

        // --- Clientes ---
        crearCliente(empresa.getId(), "Kevin Torres",   "kevin.torres@gmail.com",   "Cliente123!");
        crearCliente(empresa.getId(), "Sandra Pérez",   "sandra.perez@gmail.com",   "Cliente123!");
        crearCliente(empresa.getId(), "Miguel Rojas",   "miguel.rojas@gmail.com",   "Cliente123!");

        // --- Políticas ---
        sembrarPolitica1(empresa, dAtencion, dTecnico, dFact, dLegal);
        sembrarPolitica2(empresa, dAtencion, dTecnico, dFact);
        sembrarPolitica3(empresa, dAtencion, dFact, dRrhh);
        sembrarPolitica4(empresa, dAtencion, dLegal, dFact);
    }

    // =========================================================
    // POLÍTICA 1 — Instalación de Nuevo Medidor
    // Patrón: Lineal + Decisión
    // =========================================================

    private void sembrarPolitica1(Empresa empresa, Departamento at, Departamento te,
                                   Departamento fa, Departamento le) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Instalación de Nuevo Medidor")
                .descripcion("Flujo lineal con decisión documental: Atención → Técnico → Facturación → Legal.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        // --- Nodos ---
        Nodo inicio     = crearNodo(pol.getId(), null,       "INICIO",  "Inicio", 100, 200);
        Nodo nodo1      = crearNodo(pol.getId(), at.getId(), "TAREA",   "Recepción de Solicitud", 300, 200);
        Nodo nodo2      = crearNodo(pol.getId(), at.getId(), "TAREA",   "Verificación de Documentación", 500, 200);
        Nodo nodo3      = crearNodo(pol.getId(), te.getId(), "TAREA",   "Inspección Técnica", 700, 200);
        Nodo nodo4      = crearNodo(pol.getId(), fa.getId(), "TAREA",   "Registro de Pago", 900, 200);
        Nodo nodo5      = crearNodo(pol.getId(), le.getId(), "TAREA",   "Firma de Contrato", 1100, 200);
        Nodo fin        = crearNodo(pol.getId(), null,       "FIN",     "Fin", 1300, 200);
        Nodo finRechazo = crearNodo(pol.getId(), null,       "FIN",     "Fin — Rechazado", 500, 400);

        // --- Formulario Nodo 1 (rellena el CLIENTE) ---
        Formulario fNodo1 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo1.getId())
                .nombre("Solicitud de instalación de medidor")
                .activo(true)
                .campos(List.of(
                        campo("nombre_completo",       "Nombre completo del titular",                     "TEXTO",     true,  false, null),
                        campo("numero_medidor",         "Número de medidor actual (si existe)",            "NUMERO",    false, false, null),
                        campo("direccion_instalacion",  "Dirección exacta de instalación",                 "TEXTO",     true,  false, null),
                        campo("fecha_disponible",       "Fecha disponible para la visita técnica",         "FECHA",     true,  false, null),
                        campo("foto_domicilio",         "Foto del domicilio o terreno",                    "IMAGEN",    true,  false, null),
                        campo("cedula_identidad",       "Cédula de identidad (PDF o imagen)",              "ARCHIVO",   true,  false, null),
                        campo("tipo_instalacion",       "Tipo de instalación",                             "SELECCION", true,  false,
                                List.of("Residencial", "Comercial", "Industrial"))
                ))
                .build());
        nodo1.setFormularioId(fNodo1.getId());
        nodoRepository.save(nodo1);

        // --- Formulario Nodo 2 ---
        Formulario fNodo2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo2.getId())
                .nombre("Verificación de documentación")
                .activo(true)
                .campos(List.of(
                        campo("resultado_verificacion",  "Resultado de verificación", "SELECCION", true, true,
                                List.of("Aprobado", "Rechazado")),
                        campo("observaciones_atencion",  "Observaciones",             "TEXTO",     false, false, null)
                ))
                .build());
        nodo2.setFormularioId(fNodo2.getId());
        nodoRepository.save(nodo2);

        // --- Formulario Nodo 3 ---
        Formulario fNodo3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo3.getId())
                .nombre("Inspección técnica")
                .activo(true)
                .campos(List.of(
                        campo("resultado_inspeccion",  "Resultado de inspección",              "SELECCION", true,  true,
                                List.of("Viable", "No Viable")),
                        campo("informe_tecnico",        "Informe técnico (Word o PDF)",         "ARCHIVO",   true,  false, null),
                        campo("planilla_materiales",    "Planilla de materiales requeridos (Excel)", "ARCHIVO", true, false, null),
                        campo("costo_estimado",         "Costo estimado en Bs.",                "NUMERO",    true,  false, null),
                        campo("fotos_inspeccion",       "Fotos de la inspección",               "IMAGEN",    true,  false, null)
                ))
                .build());
        nodo3.setFormularioId(fNodo3.getId());
        nodoRepository.save(nodo3);

        // --- Formulario Nodo 4 ---
        Formulario fNodo4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo4.getId())
                .nombre("Registro de pago")
                .activo(true)
                .campos(List.of(
                        campo("numero_factura",   "Número de factura",                       "TEXTO",     true,  false, null),
                        campo("monto_pagado",     "Monto pagado en Bs.",                     "NUMERO",    true,  false, null),
                        campo("fecha_pago",       "Fecha de pago",                           "FECHA",     true,  false, null),
                        campo("comprobante_pago", "Comprobante de pago (imagen o PDF)",      "ARCHIVO",   true,  false, null),
                        campo("estado_pago",      "Estado del pago",                         "SELECCION", true,  true,
                                List.of("Aprobado", "Pendiente"))
                ))
                .build());
        nodo4.setFormularioId(fNodo4.getId());
        nodoRepository.save(nodo4);

        // --- Formulario Nodo 5 ---
        Formulario fNodo5 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo5.getId())
                .nombre("Firma de contrato")
                .activo(true)
                .campos(List.of(
                        campo("contrato_servicio",  "Contrato de servicio (Word — editar en OnlyOffice)", "ARCHIVO",   true,  false, null),
                        campo("numero_contrato",    "Número de contrato asignado",                        "TEXTO",     true,  false, null),
                        campo("resultado_firma",    "Resultado",                                           "SELECCION", true,  true,
                                List.of("Aprobado", "Rechazado"))
                ))
                .build());
        nodo5.setFormularioId(fNodo5.getId());
        nodoRepository.save(nodo5);

        // --- Transiciones ---
        crearTransicion(pol.getId(), inicio.getId(), nodo1.getId(),     "LINEAL",      null,         null);
        crearTransicion(pol.getId(), nodo1.getId(),  nodo2.getId(),     "LINEAL",      null,         null);
        crearTransicion(pol.getId(), nodo2.getId(),  nodo3.getId(),     "ALTERNATIVA", "Aprobado",   null);
        crearTransicion(pol.getId(), nodo2.getId(),  finRechazo.getId(),"ALTERNATIVA", "Rechazado",  null);
        crearTransicion(pol.getId(), nodo3.getId(),  nodo4.getId(),     "LINEAL",      null,         null);
        crearTransicion(pol.getId(), nodo4.getId(),  nodo5.getId(),     "LINEAL",      null,         null);
        crearTransicion(pol.getId(), nodo5.getId(),  fin.getId(),       "LINEAL",      null,         null);

        aplicarLayoutSeeder(
                List.of(inicio, nodo1, nodo2, nodo3, nodo4, nodo5, fin, finRechazo),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, te, fa, le)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
        log.info("Política 1 — Instalación de Nuevo Medidor creada.");
    }

    // =========================================================
    // POLÍTICA 2 — Reconexión de Servicio
    // Patrón: Fork/Join Paralelo + Decisión
    // =========================================================

    private void sembrarPolitica2(Empresa empresa, Departamento at, Departamento te, Departamento fa) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Reconexión de Servicio")
                .descripcion("Fork/join paralelo: verificación de deuda + técnica en simultáneo, luego decisión.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        // --- Nodos ---
        Nodo inicio     = crearNodo(pol.getId(), null,       "INICIO",    "Inicio", 100, 200);
        Nodo nodo1      = crearNodo(pol.getId(), at.getId(), "TAREA",     "Recepción de Solicitud de Reconexión", 300, 200);
        Nodo fork       = crearNodo(pol.getId(), at.getId(), "PARALELO",  "Fork", 500, 200);
        Nodo nodo2      = crearNodo(pol.getId(), fa.getId(), "TAREA",     "Verificación de Deuda", 400, 350);
        Nodo nodo3      = crearNodo(pol.getId(), te.getId(), "TAREA",     "Verificación Técnica", 700, 350);
        Nodo join       = crearNodo(pol.getId(), te.getId(), "PARALELO",  "Join", 500, 500);
        Nodo nodo4      = crearNodo(pol.getId(), te.getId(), "TAREA",     "Decisión y Ejecución de Reconexión", 500, 650);
        Nodo fin        = crearNodo(pol.getId(), null,       "FIN",       "Fin — Reconexión exitosa", 300, 800);
        Nodo finRechazo = crearNodo(pol.getId(), null,       "FIN",       "Fin — Impedimento técnico", 700, 800);

        // --- Formulario Nodo 1 (rellena el CLIENTE) ---
        Formulario fNodo1 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo1.getId())
                .nombre("Solicitud de reconexión de servicio")
                .activo(true)
                .campos(List.of(
                        campo("nombre_titular",  "Nombre del titular del servicio",                        "TEXTO",     true,  false, null),
                        campo("numero_socio",    "Número de socio CRE",                                    "NUMERO",    true,  false, null),
                        campo("direccion",       "Dirección del servicio",                                 "TEXTO",     true,  false, null),
                        campo("motivo_corte",    "¿Por qué fue cortado el servicio?",                      "SELECCION", true,  false,
                                List.of("Falta de pago", "Solicitud voluntaria", "Problema técnico", "Otro")),
                        campo("cedula_socio",    "Cédula de identidad (imagen)",                           "IMAGEN",    true,  false, null),
                        campo("video_medidor",   "Video del estado actual del medidor (MP4, máx 50MB)",    "ARCHIVO",   true,  false, null)
                ))
                .build());
        nodo1.setFormularioId(fNodo1.getId());
        nodoRepository.save(nodo1);

        // --- Formulario Nodo 2 (Verificación de Deuda) ---
        Formulario fNodo2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo2.getId())
                .nombre("Verificación de deuda")
                .activo(true)
                .campos(List.of(
                        campo("saldo_pendiente",   "Saldo pendiente en Bs.",                          "NUMERO",    true,  false, null),
                        campo("estado_deuda",      "Estado de la deuda",                              "SELECCION", true,  true,
                                List.of("Saldada", "Pendiente")),
                        campo("comprobante_pago",  "Comprobante de pago de deuda (si aplica)",        "ARCHIVO",   false, false, null),
                        campo("planilla_deuda",    "Planilla de deuda histórica (Excel)",             "ARCHIVO",   true,  false, null)
                ))
                .build());
        nodo2.setFormularioId(fNodo2.getId());
        nodoRepository.save(nodo2);

        // --- Formulario Nodo 3 (Verificación Técnica) ---
        Formulario fNodo3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo3.getId())
                .nombre("Verificación técnica de instalación")
                .activo(true)
                .campos(List.of(
                        campo("estado_instalacion", "Estado de la instalación",            "SELECCION", true, true,
                                List.of("Apto para reconexión", "No apto — requiere reparación")),
                        campo("foto_instalacion",   "Foto del estado de la instalación",  "IMAGEN",    true, false, null),
                        campo("informe_tecnico",    "Informe técnico (Word)",              "ARCHIVO",   true, false, null)
                ))
                .build());
        nodo3.setFormularioId(fNodo3.getId());
        nodoRepository.save(nodo3);

        // --- Formulario Nodo 4 (Decisión y Ejecución) ---
        Formulario fNodo4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo4.getId())
                .nombre("Decisión y ejecución de reconexión")
                .activo(true)
                .campos(List.of(
                        campo("resultado_reconexion",       "Resultado de la reconexión",    "SELECCION", true,  true,
                                List.of("Reconexión exitosa", "Impedimento técnico")),
                        campo("fecha_hora_reconexion",      "Fecha y hora de la reconexión", "FECHA",     true,  false, null),
                        campo("foto_medidor_reconectado",   "Foto del medidor reconectado",  "IMAGEN",    true,  false, null),
                        campo("observaciones",              "Observaciones adicionales",      "TEXTO",     false, false, null)
                ))
                .build());
        nodo4.setFormularioId(fNodo4.getId());
        nodoRepository.save(nodo4);

        // --- Transiciones ---
        crearTransicion(pol.getId(), inicio.getId(), nodo1.getId(),     "LINEAL",    null,                    null);
        crearTransicion(pol.getId(), nodo1.getId(),  fork.getId(),      "LINEAL",    null,                    null);
        crearTransicion(pol.getId(), fork.getId(),   nodo2.getId(),     "PARALELA",  null,                    null);
        crearTransicion(pol.getId(), fork.getId(),   nodo3.getId(),     "PARALELA",  null,                    null);
        crearTransicion(pol.getId(), nodo2.getId(),  join.getId(),      "PARALELA",  null,                    null);
        crearTransicion(pol.getId(), nodo3.getId(),  join.getId(),      "PARALELA",  null,                    null);
        crearTransicion(pol.getId(), join.getId(),   nodo4.getId(),     "LINEAL",    null,                    null);
        crearTransicion(pol.getId(), nodo4.getId(),  fin.getId(),       "ALTERNATIVA","Reconexión exitosa",   null);
        crearTransicion(pol.getId(), nodo4.getId(),  finRechazo.getId(),"ALTERNATIVA","Impedimento técnico",  null);

        aplicarLayoutSeeder(
                List.of(inicio, nodo1, fork, nodo2, nodo3, join, nodo4, fin, finRechazo),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, fa, te)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
        log.info("Política 2 — Reconexión de Servicio creada.");
    }

    // =========================================================
    // POLÍTICA 3 — Reclamo por Facturación Incorrecta
    // Patrón: Decisión doble + ciclo de revisión
    // =========================================================

    private void sembrarPolitica3(Empresa empresa, Departamento at, Departamento fa, Departamento rrhh) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Reclamo por Facturación Incorrecta")
                .descripcion("Análisis de consumo histórico, nota de crédito o explicación al socio, escalado a supervisor.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        // --- Nodos ---
        Nodo inicio  = crearNodo(pol.getId(), null,        "INICIO",  "Inicio", 100, 200);
        Nodo nodo1   = crearNodo(pol.getId(), at.getId(),  "TAREA",   "Registro del Reclamo", 300, 200);
        Nodo nodo2   = crearNodo(pol.getId(), fa.getId(),  "TAREA",   "Análisis de Consumo Histórico", 500, 200);
        Nodo nodo3   = crearNodo(pol.getId(), fa.getId(),  "TAREA",   "Nota de Crédito", 500, 400);
        Nodo nodo4   = crearNodo(pol.getId(), at.getId(),  "TAREA",   "Explicación al Socio", 700, 400);
        Nodo nodo5   = crearNodo(pol.getId(), rrhh.getId(),"TAREA",   "Escalado a Supervisor", 900, 400);
        Nodo fin     = crearNodo(pol.getId(), null,        "FIN",     "Fin", 700, 600);

        // --- Formulario Nodo 1 (rellena el CLIENTE) ---
        Formulario fNodo1 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo1.getId())
                .nombre("Registro del reclamo de facturación")
                .activo(true)
                .campos(List.of(
                        campo("nombre_socio",         "Nombre completo del socio",                               "TEXTO",     true,  false, null),
                        campo("numero_socio",         "Número de socio",                                         "NUMERO",    true,  false, null),
                        campo("mes_reclamado",        "Mes de facturación reclamado",                            "SELECCION", true,  false,
                                List.of("Enero","Febrero","Marzo","Abril","Mayo","Junio",
                                        "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")),
                        campo("monto_facturado",      "Monto facturado que considera incorrecto (Bs.)",          "NUMERO",    true,  false, null),
                        campo("monto_esperado",       "Monto que esperaba pagar (estimado en Bs.)",              "NUMERO",    true,  false, null),
                        campo("factura_imagen",       "Foto o scan de la factura reclamada",                     "IMAGEN",    true,  false, null),
                        campo("descripcion_reclamo",  "Descripción detallada del reclamo",                       "TEXTO",     true,  false, null),
                        campo("video_medidor_actual", "Video del medidor mostrando lectura actual (opcional)",   "ARCHIVO",   false, false, null)
                ))
                .build());
        nodo1.setFormularioId(fNodo1.getId());
        nodoRepository.save(nodo1);

        // --- Formulario Nodo 2 ---
        Formulario fNodo2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo2.getId())
                .nombre("Análisis de consumo histórico")
                .activo(true)
                .campos(List.of(
                        campo("historial_consumo",       "Historial de consumo últimos 12 meses (Excel)",    "ARCHIVO",   true,  false, null),
                        campo("consumo_mes_reclamado",   "Consumo real del mes reclamado (kWh)",             "NUMERO",    true,  false, null),
                        campo("consumo_promedio",        "Consumo promedio últimos 6 meses (kWh)",           "NUMERO",    true,  false, null),
                        campo("diferencia_porcentaje",   "Diferencia porcentual (%)",                        "NUMERO",    true,  false, null),
                        campo("conclusion_analisis",     "¿Se confirma el error de facturación?",            "SELECCION", true,  true,
                                List.of("Sí — error confirmado", "No — facturación correcta"))
                ))
                .build());
        nodo2.setFormularioId(fNodo2.getId());
        nodoRepository.save(nodo2);

        // --- Formulario Nodo 3 ---
        Formulario fNodo3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo3.getId())
                .nombre("Nota de crédito")
                .activo(true)
                .campos(List.of(
                        campo("numero_nota_credito", "Número de nota de crédito",          "TEXTO",     true,  false, null),
                        campo("monto_acreditado",    "Monto acreditado al socio (Bs.)",    "NUMERO",    true,  false, null),
                        campo("nota_credito_pdf",    "Nota de crédito firmada (PDF)",      "ARCHIVO",   true,  false, null),
                        campo("resultado_nota",      "Nota de crédito emitida",            "SELECCION", true,  true,
                                List.of("Aprobado"))
                ))
                .build());
        nodo3.setFormularioId(fNodo3.getId());
        nodoRepository.save(nodo3);

        // --- Formulario Nodo 4 ---
        Formulario fNodo4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo4.getId())
                .nombre("Explicación al socio")
                .activo(true)
                .campos(List.of(
                        campo("respuesta_socio",      "¿El socio acepta la explicación?",              "SELECCION", true,  true,
                                List.of("Acepta", "No acepta")),
                        campo("informe_explicacion",  "Informe de la explicación dada (Word)",          "ARCHIVO",   true,  false, null)
                ))
                .build());
        nodo4.setFormularioId(fNodo4.getId());
        nodoRepository.save(nodo4);

        // --- Formulario Nodo 5 ---
        Formulario fNodo5 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo5.getId())
                .nombre("Escalado a supervisor")
                .activo(true)
                .campos(List.of(
                        campo("decision_supervisor",      "Decisión del supervisor",                               "SELECCION", true,  true,
                                List.of("Emitir nota de crédito de cortesía", "Mantener facturación correcta")),
                        campo("acta_supervision",         "Acta de supervisión (Word — editar en OnlyOffice)",     "ARCHIVO",   true,  false, null),
                        campo("observaciones_supervisor", "Observaciones del supervisor",                          "TEXTO",     false, false, null)
                ))
                .build());
        nodo5.setFormularioId(fNodo5.getId());
        nodoRepository.save(nodo5);

        // --- Transiciones ---
        crearTransicion(pol.getId(), inicio.getId(), nodo1.getId(), "LINEAL",      null,                       null);
        crearTransicion(pol.getId(), nodo1.getId(),  nodo2.getId(), "LINEAL",      null,                       null);
        crearTransicion(pol.getId(), nodo2.getId(),  nodo3.getId(), "ALTERNATIVA", "Sí — error confirmado",    null);
        crearTransicion(pol.getId(), nodo2.getId(),  nodo4.getId(), "ALTERNATIVA", "No — facturación correcta",null);
        crearTransicion(pol.getId(), nodo3.getId(),  fin.getId(),   "LINEAL",      null,                       null);
        crearTransicion(pol.getId(), nodo4.getId(),  fin.getId(),   "ALTERNATIVA", "Acepta",                   null);
        crearTransicion(pol.getId(), nodo4.getId(),  nodo5.getId(), "ALTERNATIVA", "No acepta",                null);
        crearTransicion(pol.getId(), nodo5.getId(),  fin.getId(),   "LINEAL",      null,                       null);

        aplicarLayoutSeeder(
                List.of(inicio, nodo1, nodo2, nodo3, nodo4, nodo5, fin),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, fa, rrhh)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
        log.info("Política 3 — Reclamo por Facturación Incorrecta creada.");
    }

    // =========================================================
    // POLÍTICA 4 — Cambio de Titularidad del Medidor
    // Patrón: Lineal + Decisión documental + ciclo de revisión
    // =========================================================

    private void sembrarPolitica4(Empresa empresa, Departamento at, Departamento le, Departamento fa) {
        Politica pol = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Cambio de Titularidad del Medidor")
                .descripcion("Verificación documental, elaboración de nuevo contrato, actualización de datos de facturación.")
                .estado("BORRADOR")
                .version(1)
                .activo(true)
                .datosDiagramaJson("{\"version\":1}")
                .build());

        // --- Nodos ---
        Nodo inicio     = crearNodo(pol.getId(), null,       "INICIO",  "Inicio", 100, 200);
        Nodo nodo1      = crearNodo(pol.getId(), at.getId(), "TAREA",   "Presentación de Solicitud", 300, 200);
        Nodo nodo2      = crearNodo(pol.getId(), at.getId(), "TAREA",   "Verificación Documental", 500, 200);
        Nodo nodo3      = crearNodo(pol.getId(), le.getId(), "TAREA",   "Elaboración del Nuevo Contrato", 700, 200);
        Nodo nodo4      = crearNodo(pol.getId(), fa.getId(), "TAREA",   "Actualización de Datos de Facturación", 900, 200);
        Nodo fin        = crearNodo(pol.getId(), null,       "FIN",     "Fin", 1100, 200);
        Nodo finRechazo = crearNodo(pol.getId(), null,       "FIN",     "Fin — Documentación inválida", 500, 400);

        // --- Formulario Nodo 1 (rellena el CLIENTE — el que más documentos pide) ---
        Formulario fNodo1 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo1.getId())
                .nombre("Solicitud de cambio de titularidad")
                .activo(true)
                .campos(List.of(
                        campo("nombre_cedente",    "Nombre completo del titular actual (cedente)",           "TEXTO",     true,  false, null),
                        campo("ci_cedente",        "Cédula del titular actual (imagen o PDF)",               "ARCHIVO",   true,  false, null),
                        campo("nombre_cesionario", "Nombre completo del nuevo titular (cesionario)",         "TEXTO",     true,  false, null),
                        campo("ci_cesionario",     "Cédula del nuevo titular (imagen o PDF)",                "ARCHIVO",   true,  false, null),
                        campo("poder_notarial",    "Poder notarial o declaración de transferencia (PDF)",    "ARCHIVO",   true,  false, null),
                        campo("planilla_datos",    "Planilla de datos del nuevo titular (Excel)",            "ARCHIVO",   true,  false, null),
                        campo("numero_medidor",    "Número del medidor a transferir",                        "NUMERO",    true,  false, null),
                        campo("foto_medidor",      "Foto actual del medidor",                                "IMAGEN",    true,  false, null),
                        campo("tipo_inmueble",     "Tipo de inmueble",                                       "SELECCION", true,  false,
                                List.of("Casa", "Departamento", "Local comercial", "Terreno", "Otro"))
                ))
                .build());
        nodo1.setFormularioId(fNodo1.getId());
        nodoRepository.save(nodo1);

        // --- Formulario Nodo 2 ---
        Formulario fNodo2 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo2.getId())
                .nombre("Verificación documental")
                .activo(true)
                .campos(List.of(
                        campo("documentos_completos",    "¿Documentación completa y válida?",           "SELECCION", true,  true,
                                List.of("Válida — proceder", "Inválida — notificar faltantes")),
                        campo("checklist_verificacion",  "Checklist de verificación (Excel)",           "ARCHIVO",   true,  false, null),
                        campo("observaciones",           "Observaciones sobre la documentación",        "TEXTO",     false, false, null)
                ))
                .build());
        nodo2.setFormularioId(fNodo2.getId());
        nodoRepository.save(nodo2);

        // --- Formulario Nodo 3 ---
        Formulario fNodo3 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo3.getId())
                .nombre("Elaboración del nuevo contrato")
                .activo(true)
                .campos(List.of(
                        campo("nuevo_contrato",          "Nuevo contrato de servicio (Word — editar colaborativamente en OnlyOffice)",
                                "ARCHIVO",   true,  false, null),
                        campo("numero_contrato_nuevo",   "Número del nuevo contrato",                   "TEXTO",     true,  false, null),
                        campo("fecha_vigencia",          "Fecha de inicio de vigencia del nuevo contrato", "FECHA",  true,  false, null),
                        campo("resultado_contrato",      "Contrato elaborado y firmado",                "SELECCION", true,  true,
                                List.of("Aprobado", "Requiere correcciones"))
                ))
                .build());
        nodo3.setFormularioId(fNodo3.getId());
        nodoRepository.save(nodo3);

        // --- Formulario Nodo 4 ---
        Formulario fNodo4 = formularioRepository.save(Formulario.builder()
                .politicaId(pol.getId()).nodoId(nodo4.getId())
                .nombre("Actualización de datos de facturación")
                .activo(true)
                .campos(List.of(
                        campo("datos_actualizados",       "¿Datos actualizados en el sistema?",             "SELECCION", true,  true,
                                List.of("Aprobado")),
                        campo("nueva_planilla_facturacion","Nueva planilla de facturación (Excel)",          "ARCHIVO",   true,  false, null),
                        campo("fecha_primer_cobro",       "Fecha del primer cobro al nuevo titular",        "FECHA",     true,  false, null),
                        campo("captura_sistema",          "Captura de pantalla del sistema actualizado",    "IMAGEN",    true,  false, null)
                ))
                .build());
        nodo4.setFormularioId(fNodo4.getId());
        nodoRepository.save(nodo4);

        // --- Transiciones ---
        // Nodo3 → Nodo3 (ciclo de revisión) se omite en la topología del layout,
        // pero se registra como transición para el motor.
        crearTransicion(pol.getId(), inicio.getId(), nodo1.getId(),     "LINEAL",      null,                         null);
        crearTransicion(pol.getId(), nodo1.getId(),  nodo2.getId(),     "LINEAL",      null,                         null);
        crearTransicion(pol.getId(), nodo2.getId(),  nodo3.getId(),     "ALTERNATIVA", "Válida — proceder",          null);
        crearTransicion(pol.getId(), nodo2.getId(),  finRechazo.getId(),"ALTERNATIVA", "Inválida — notificar faltantes", null);
        crearTransicion(pol.getId(), nodo3.getId(),  nodo4.getId(),     "ALTERNATIVA", "Aprobado",                   null);
        crearTransicion(pol.getId(), nodo3.getId(),  nodo3.getId(),     "ALTERNATIVA", "Requiere correcciones",      null);
        crearTransicion(pol.getId(), nodo4.getId(),  fin.getId(),       "LINEAL",      null,                         null);

        aplicarLayoutSeeder(
                List.of(inicio, nodo1, nodo2, nodo3, nodo4, fin, finRechazo),
                transicionRepository.findByPoliticaIdAndActivoTrue(pol.getId()),
                List.of(at, le, fa)
        );

        pol.setEstado("ACTIVA");
        politicaRepository.save(pol);
        log.info("Política 4 — Cambio de Titularidad del Medidor creada.");
    }

    // =========================================================
    // HELPERS DE CREACIÓN
    // =========================================================

    private Empresa crearEmpresaCRE() {
        return empresaRepository.save(Empresa.builder()
                .nombre("CRE Bolivia — Cooperativa Rural de Electrificación")
                .activo(true)
                .build());
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

    private void crearAdmin(String empId, String nombre, String email, String rawPassword) {
        usuarioRepository.save(Usuario.builder()
                .empresaId(empId)
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .rol("ADMIN_GENERAL")
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build());
    }

    private Usuario crearAdminDepto(String empId, String nombre, String email,
                                     String rawPassword, Departamento depto) {
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

    private void crearFuncionario(String empId, String nombre, String email, Departamento depto) {
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

    private void crearCliente(String empId, String nombre, String email, String rawPassword) {
        usuarioRepository.save(Usuario.builder()
                .empresaId(empId)
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .rol("CLIENTE")
                .activo(true)
                .creadoEn(LocalDateTime.now())
                .build());
    }

    private Nodo crearNodo(String polId, String deptoId, String tipo, String nombre,
                            double x, double y) {
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

    private Transicion crearTransicion(String polId, String from, String to,
                                        String tipo, String etiqueta, String condicion) {
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

    private Formulario.CampoFormulario campo(String nombre, String etiqueta, String tipo,
                                              boolean req, boolean prioridad, List<String> opciones) {
        return Formulario.CampoFormulario.builder()
                .nombre(nombre)
                .etiqueta(etiqueta)
                .tipo(tipo)
                .requerido(req)
                .esCampoPrioridad(prioridad)
                .opciones(opciones)
                .build();
    }

    // =========================================================
    // LAYOUT AUTOMÁTICO (conservado íntegro del seeder anterior)
    // =========================================================

    private void aplicarLayoutSeeder(List<Nodo> nodos, List<Transicion> transiciones,
                                      List<Departamento> carriles) {
        if (nodos == null || nodos.isEmpty()) {
            return;
        }

        Map<String, Nodo> nodeById = new LinkedHashMap<>();
        for (Nodo nodo : nodos) {
            nodeById.put(nodo.getId(), nodo);
        }

        Map<String, List<String>> successors   = new HashMap<>();
        Map<String, List<String>> predecessors = new HashMap<>();
        for (String nodeId : nodeById.keySet()) {
            successors.put(nodeId, new ArrayList<>());
            predecessors.put(nodeId, new ArrayList<>());
        }

        List<Transicion> trList = transiciones == null ? List.of() : transiciones;
        for (Transicion tr : trList) {
            String from = tr.getNodoOrigenId();
            String to   = tr.getNodoDestinoId();
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
        Set<String>  seenLane  = new HashSet<>();

        if (inicioId != null) {
            Queue<String> queue   = new ArrayDeque<>();
            Set<String>   visited = new HashSet<>();
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
                    nodeId, nodeById, successors, predecessors,
                    deptoMasFrecuente, laneOrder, carriles);
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

        List<String>         topoOrder = construirOrdenTopologico(new ArrayList<>(nodeById.keySet()), successors, predecessors, inicioId);
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
            String nodeId   = nodo.getId();
            String laneId   = resolvedDeptByNode.getOrDefault(nodeId, laneOrder.get(0));
            int    laneIndex = laneIndexByDept.getOrDefault(laneId, 0);

            double nodeWidth   = anchoNodoTipo(nodo.getTipo());
            int    spanFromLane = laneIndex;
            int    spanToLane   = laneIndex;

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
                    spanToLane   = Math.max(spanToLane, candidate);
                }

                if (spanToLane > spanFromLane) {
                    nodeWidth = nodeWidth + ((spanToLane - spanFromLane) * LANE_WIDTH);
                }
            }

            double x = LANE_START_X + (laneIndex * LANE_WIDTH) + ((LANE_WIDTH - nodeWidth) / 2d);
            if (esTipoParalelo(nodo.getTipo())) {
                x = LANE_START_X + (spanFromLane * LANE_WIDTH) + 40d;
            }

            int    level = levels.getOrDefault(nodeId, 0);
            double y     = BASE_Y + (level * LEVEL_GAP);

            nodo.setPosicionX(x);
            nodo.setPosicionY(y);

            if (requiereDepartamento(nodo.getTipo())) {
                nodo.setDepartamentoId(laneId);
            }
        }

        nodoRepository.saveAll(nodos);
    }

    private String resolverDeptoNodo(String nodeId, Map<String, Nodo> nodeById,
                                      Map<String, List<String>> successors,
                                      Map<String, List<String>> predecessors,
                                      String deptoMasFrecuente, List<String> laneOrder,
                                      List<Departamento> carriles) {
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

    private String buscarDeptoMasCercano(String startNodeId,
                                          Map<String, List<String>> adjacency,
                                          Map<String, Nodo> nodeById) {
        Queue<String> queue   = new ArrayDeque<>();
        Set<String>   visited = new HashSet<>();
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

    private List<String> construirOrdenTopologico(List<String> nodeIds,
                                                   Map<String, List<String>> successors,
                                                   Map<String, List<String>> predecessors,
                                                   String inicioId) {
        Map<String, Integer> indegree = new HashMap<>();
        for (String nodeId : nodeIds) {
            indegree.put(nodeId, predecessors.getOrDefault(nodeId, List.of()).size());
        }

        Queue<String> queue  = new ArrayDeque<>();
        Set<String>   queued = new HashSet<>();
        if (inicioId != null && indegree.getOrDefault(inicioId, 0) == 0) {
            queue.add(inicioId);
            queued.add(inicioId);
        }

        for (String nodeId : nodeIds) {
            if (indegree.getOrDefault(nodeId, 0) == 0 && queued.add(nodeId)) {
                queue.add(nodeId);
            }
        }

        List<String> order     = new ArrayList<>();
        Set<String>  processed = new HashSet<>();

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
            Set<String>   bfsSeen  = new HashSet<>();
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

    private void alinearNivelesDeRamas(Map<String, Integer> levels,
                                        Map<String, Integer> topoIndex,
                                        Map<String, List<String>> successors,
                                        int maxIterations) {
        boolean changed = true;
        int     guard   = 0;

        while (changed && guard < maxIterations) {
            changed = false;
            guard++;

            for (Map.Entry<String, List<String>> entry : successors.entrySet()) {
                String       nodeId   = entry.getKey();
                Integer      srcIndex = topoIndex.get(nodeId);
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
                String  srcId    = entry.getKey();
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
}
