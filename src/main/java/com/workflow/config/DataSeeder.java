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
import com.workflow.tramite.model.Tramite;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.transicion.model.Transicion;
import com.workflow.transicion.repository.TransicionRepository;
import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

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

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (empresaRepository.count() > 0) {
                log.info("Base de datos ya poblada. Se salta el seeder.");
                return;
            }

            log.info("Iniciando DataSeeder CRE Santa Cruz...");

            Empresa empresa = empresaRepository.save(Empresa.builder()
                    .nombre("CRE Santa Cruz")
                    .activo(true)
                    .build());

            // Admin general
            Usuario adminGen = usuarioRepository.save(Usuario.builder()
                    .empresaId(empresa.getId())
                    .nombre("Admin General Seeder")
                    .email("admin@cre.bo")
                    .passwordHash("Admin123!") // En el mundo real se codifica
                    .rol("ADMIN_GENERAL")
                    .activo(true)
                    .build());

            // Departamentos
            Departamento dptoAtencion = crearDepto(empresa.getId(), "Atencion al Cliente");
            Departamento dptoTecnico = crearDepto(empresa.getId(), "Tecnico");
            Departamento dptoFacturacion = crearDepto(empresa.getId(), "Facturacion");
            Departamento dptoLegal = crearDepto(empresa.getId(), "Legal");
            Departamento dptoRrhh = crearDepto(empresa.getId(), "Recursos Humanos");

            // Admins por departamento
            Usuario admAtencion = crearUsuario(empresa.getId(), "Admin Atencion", "admin.atencion@cre.bo", "ADMIN_DEPARTAMENTO", dptoAtencion);
            Usuario admTecnico = crearUsuario(empresa.getId(), "Admin Tecnico", "admin.tecnico@cre.bo", "ADMIN_DEPARTAMENTO", dptoTecnico);
            Usuario admFacturacion = crearUsuario(empresa.getId(), "Admin Facturacion", "admin.facturacion@cre.bo", "ADMIN_DEPARTAMENTO", dptoFacturacion);
            Usuario admLegal = crearUsuario(empresa.getId(), "Admin Legal", "admin.legal@cre.bo", "ADMIN_DEPARTAMENTO", dptoLegal);
            Usuario admRrhh = crearUsuario(empresa.getId(), "Admin RRHH", "admin.rrhh@cre.bo", "ADMIN_DEPARTAMENTO", dptoRrhh);

            // Funcionarios
            crearUsuario(empresa.getId(), "Funcionario 1 Atencion", "func1.atencion@cre.bo", "FUNCIONARIO", dptoAtencion);
            crearUsuario(empresa.getId(), "Funcionario 2 Atencion", "func2.atencion@cre.bo", "FUNCIONARIO", dptoAtencion);
            crearUsuario(empresa.getId(), "Funcionario 1 Tecnico", "func1.tecnico@cre.bo", "FUNCIONARIO", dptoTecnico);
            crearUsuario(empresa.getId(), "Funcionario 1 Facturacion", "func1.facturacion@cre.bo", "FUNCIONARIO", dptoFacturacion);
            crearUsuario(empresa.getId(), "Funcionario 1 Legal", "func1.legal@cre.bo", "FUNCIONARIO", dptoLegal);
            
            // ================== POLITICA 1 ==================
            Politica pol1 = politicaRepository.save(Politica.builder()
                    .empresaId(empresa.getId())
                    .nombre("Instalacion de Nuevo Medidor")
                    .estado("ACTIVA")
                    .version(1)
                    .activo(true)
                    .build());

            Nodo p1n1 = crearNodo(pol1.getId(), dptoAtencion.getId(), "INICIO", "Inicio Solicitud");
            Nodo p1n2 = crearNodo(pol1.getId(), dptoAtencion.getId(), "TAREA", "Recibir solicitud");
            Nodo p1n3 = crearNodo(pol1.getId(), dptoAtencion.getId(), "DECISION", "¿Documentacion completa?");
            
            // Formularios para TAREA
            Formulario formRecibir = formularioRepository.save(Formulario.builder()
                .politicaId(pol1.getId())
                .nodoId(p1n2.getId())
                .nombre("Formulario Solicitud")
                .activo(true)
                .campos(List.of(
                    new Formulario.CampoFormulario("ci_titular", "CI Titular", "TEXTO", true, false, null)
                ))
                .build());
            
            Nodo p1n3_aprobado = crearNodo(pol1.getId(), dptoTecnico.getId(), "TAREA", "Realizar inspeccion");
            Nodo p1n3_rechazado = crearNodo(pol1.getId(), dptoAtencion.getId(), "TAREA", "Notificar cliente");
            Nodo p1nEndA = crearNodo(pol1.getId(), null, "FIN", "Fin Instalado");
            Nodo p1nEndR = crearNodo(pol1.getId(), null, "FIN", "Fin Rechazado");

            crearTransicion(pol1.getId(), p1n1.getId(), p1n2.getId(), "LINEAL", null);
            crearTransicion(pol1.getId(), p1n2.getId(), p1n3.getId(), "LINEAL", null);
            crearTransicion(pol1.getId(), p1n3.getId(), p1n3_aprobado.getId(), "ALTERNATIVA", "Aprobado");
            crearTransicion(pol1.getId(), p1n3.getId(), p1n3_rechazado.getId(), "ALTERNATIVA", "Rechazado");
            crearTransicion(pol1.getId(), p1n3_aprobado.getId(), p1nEndA.getId(), "LINEAL", null);
            crearTransicion(pol1.getId(), p1n3_rechazado.getId(), p1nEndR.getId(), "LINEAL", null);

            log.info("DataSeeder finalizado con éxito.");
        };
    }

    private Departamento crearDepto(String empresaId, String nombre) {
        Departamento d = new Departamento();
        d.setEmpresaId(empresaId);
        d.setNombre(nombre);
        d.setActivo(true);
        return departamentoRepository.save(d);
    }

    private Usuario crearUsuario(String empId, String nombre, String email, String rol, Departamento depto) {
        Usuario u = usuarioRepository.save(Usuario.builder()
                .empresaId(empId)
                .departamentoId(depto.getId())
                .nombre(nombre)
                .email(email)
                .passwordHash("Func123!") // placeholder
                .rol(rol)
                .activo(true)
                .build());

        if ("ADMIN_DEPARTAMENTO".equals(rol)) {
            depto.setAdminDepartamentoId(u.getId());
            departamentoRepository.save(depto);
        }
        return u;
    }

    private Nodo crearNodo(String polId, String deptoId, String tipo, String nombre) {
        return nodoRepository.save(Nodo.builder()
                .politicaId(polId)
                .departamentoId(deptoId)
                .tipo(tipo)
                .nombre(nombre)
                .posicionX(100.0)
                .posicionY(100.0)
                .build());
    }

    private Transicion crearTransicion(String polId, String from, String to, String tipo, String etiqueta) {
        return transicionRepository.save(Transicion.builder()
                .politicaId(polId)
                .nodoOrigenId(from)
                .nodoDestinoId(to)
                .tipo(tipo)
                .etiqueta(etiqueta)
                .build());
    }
}
