package com.workflow.security;

import com.workflow.usuario.model.Usuario;
import com.workflow.usuario.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class EnterpriseContextFilter extends OncePerRequestFilter {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Extraer userId del request (establecido por JwtFilter)
        String userId = (String) request.getAttribute("userId");

        if (userId != null) {
            try {
                // Obtener usuario y su empresaId
                var usuario = usuarioRepository.findById(userId);
                if (usuario.isPresent()) {
                    String empresaId = usuario.get().getEmpresaId();
                    // Agregar empresaId al request para que lo usen los controllers
                    request.setAttribute("X-Empresa-Id", empresaId);
                }
            } catch (Exception e) {
                log.debug("Error al obtener empresaId: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
