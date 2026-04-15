package com.workflow.seeder;

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
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.notificacion.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public String seedDia4() {
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre("Empresa Seeder Workflow")
                .activo(true)
                .build());

        Usuario adminGeneral = usuarioRepository.save(Usuario.builder()
                .empresaId(empresa.getId())
                .nombre("Admin General Seeder")
                .email("admin.general@seed.local")
                .passwordHash(passwordEncoder.encode("123456"))
                .rol("ADMIN_GENERAL")
                .activo(true)
                .build());

        Departamento d1 = new Departamento();
        d1.setEmpresaId(empresa.getId());
        d1.setNombre("Atencion");
        d1.setDescripcion("Area de atencion inicial");
        d1.setActivo(true);
        d1 = departamentoRepository.save(d1);

        Departamento d2 = new Departamento();
        d2.setEmpresaId(empresa.getId());
        d2.setNombre("Tecnico");
        d2.setDescripcion("Area tecnica");
        d2.setActivo(true);
        d2 = departamentoRepository.save(d2);

        Usuario adminDepto = usuarioRepository.save(Usuario.builder()
                .empresaId(empresa.getId())
                .nombre("Admin Depto Seeder")
                .email("admin.depto@seed.local")
                .passwordHash(passwordEncoder.encode("123456"))
                .rol("ADMIN_DEPARTAMENTO")
                .departamentoId(d1.getId())
                .activo(true)
                .build());

        usuarioRepository.save(Usuario.builder()
                .empresaId(empresa.getId())
                .nombre("Funcionario Seeder")
                .email("funcionario@seed.local")
                .passwordHash(passwordEncoder.encode("123456"))
                .rol("FUNCIONARIO")
                .departamentoId(d2.getId())
                .activo(true)
                .build());

        d1.setAdminDepartamentoId(adminDepto.getId());
        departamentoRepository.save(d1);

        Politica politica = politicaRepository.save(Politica.builder()
                .empresaId(empresa.getId())
                .nombre("Politica Seeder Dia 4")
                .descripcion("Flujo con editor")
                .version(1)
                .estado("BORRADOR")
                .generadaPorIa(false)
                .creadoPor(adminGeneral.getId())
                .activo(true)
                .build());

        Nodo nInicio = nodoRepository.save(Nodo.builder()
                .politicaId(politica.getId())
                .departamentoId(d1.getId())
                .nombre("Inicio")
                .tipo("INICIO")
                .posicionX(80D)
                .posicionY(120D)
                .activo(true)
                .build());
        Nodo nTarea = nodoRepository.save(Nodo.builder()
                .politicaId(politica.getId())
                .departamentoId(d1.getId())
                .nombre("Revision Inicial")
                .tipo("TAREA")
                .posicionX(300D)
                .posicionY(120D)
                .activo(true)
                .build());
        Nodo nFin = nodoRepository.save(Nodo.builder()
                .politicaId(politica.getId())
                .departamentoId(d2.getId())
                .nombre("Fin")
                .tipo("FIN")
                .posicionX(560D)
                .posicionY(260D)
                .activo(true)
                .build());

        transicionRepository.saveAll(List.of(
                Transicion.builder()
                        .politicaId(politica.getId())
                        .nodoOrigenId(nInicio.getId())
                        .nodoDestinoId(nTarea.getId())
                        .tipo("LINEAL")
                        .etiqueta("Continuar")
                        .activo(true)
                        .build(),
                Transicion.builder()
                        .politicaId(politica.getId())
                        .nodoOrigenId(nTarea.getId())
                        .nodoDestinoId(nFin.getId())
                        .tipo("LINEAL")
                        .etiqueta("Completar")
                        .activo(true)
                        .build()
        ));

        Formulario formulario = formularioRepository.save(Formulario.builder()
                .politicaId(politica.getId())
                .nodoId(nTarea.getId())
                .nombre("Formulario Revision")
                .generadoPorIa(false)
                .creadoPor(adminDepto.getId())
                .activo(true)
                .campos(List.of(
                        Formulario.CampoFormulario.builder()
                                .nombre("detalle")
                                .etiqueta("Detalle")
                                .tipo("TEXTO")
                                .requerido(true)
                                .esCampoPrioridad(false)
                                .opciones(List.of())
                                .build(),
                        Formulario.CampoFormulario.builder()
                                .nombre("prioridad")
                                .etiqueta("Prioridad")
                                .tipo("SELECCION")
                                .requerido(true)
                                .esCampoPrioridad(true)
                                .opciones(List.of("ALTA", "MEDIA", "BAJA"))
                                .build()
                ))
                .build());

        nTarea.setFormularioId(formulario.getId());
        nodoRepository.save(nTarea);

        politica.setEstado("ACTIVA");
        politicaRepository.save(politica);

        return "Seeder dia4 aplicado. Credenciales: admin.general@seed.local / 123456";
    }

    public String seedDia6() {
        this.clearAll();
        this.seedDia4();

        // Encontrar politica
        Politica p = politicaRepository.findAll().get(0);
        Empresa e = empresaRepository.findAll().get(0);
        Usuario adminG = usuarioRepository.findByEmail("admin.general@seed.local").get();

        // Iniciar tramite mock
        com.workflow.tramite.model.Tramite tramite = com.workflow.tramite.model.Tramite.builder()
            .politicaId(p.getId())
            .empresaId(e.getId())
            .titulo("Trámite de Prueba Seeder")
            .prioridad("ALTA")
            .estadoGeneral("EN_PROCESO")
            .iniciadoPor(adminG.getId())
            .iniciadoEn(java.time.LocalDateTime.now())
            .build();

        // Obtener primer nodo
        Nodo n1 = nodoRepository.findByPoliticaId(p.getId()).stream()
            .filter(n -> "INICIO".equals(n.getTipo())).findFirst().get();

        tramite.setNodoActualId(n1.getId());
        tramite = tramiteRepository.save(tramite);

        // Al crear el tramite en INICIO, el motor genera la primera ejecucion para la primera TAREA. Para simular el motor, haremos q el primer nodo TAREA ya este pendiente:
        Nodo nTarea = nodoRepository.findByPoliticaId(p.getId()).stream()
            .filter(n -> "TAREA".equals(n.getTipo())).findFirst().get();
        tramite.setNodoActualId(nTarea.getId());
        tramite = tramiteRepository.save(tramite);

        com.workflow.ejecucion.model.EjecucionNodo ejecucion = com.workflow.ejecucion.model.EjecucionNodo.builder()
            .tramiteId(tramite.getId())
            .nodoId(nTarea.getId())
            .departamentoId(nTarea.getDepartamentoId())
            .estado("PENDIENTE")
            .iniciadoEn(java.time.LocalDateTime.now())
            .build();
        
        ejecucionNodoRepository.save(ejecucion);

        return "Seeder dia6 aplicado. Tramite pendiente disponible.";
    }

    public String clearAll() {
        formularioRepository.deleteAll();
        transicionRepository.deleteAll();
        nodoRepository.deleteAll();
        politicaRepository.deleteAll();
        departamentoRepository.deleteAll();
        usuarioRepository.deleteAll();
        empresaRepository.deleteAll();
        return "Datos eliminados";
    }
}
