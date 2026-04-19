package com.workflow.seeder;

import com.workflow.config.DataSeeder;
import com.workflow.departamento.repository.DepartamentoRepository;
import com.workflow.ejecucion.repository.EjecucionNodoRepository;
import com.workflow.empresa.repository.EmpresaRepository;
import com.workflow.formulario.repository.FormularioRepository;
import com.workflow.nodo.repository.NodoRepository;
import com.workflow.notificacion.repository.NotificacionRepository;
import com.workflow.politica.repository.PoliticaRepository;
import com.workflow.tramite.repository.TramiteRepository;
import com.workflow.transicion.repository.TransicionRepository;
import com.workflow.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final DataSeeder dataSeeder;

    public String seedAll() {
        return dataSeeder.ejecutarSeedManualSiVacia();
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
}
