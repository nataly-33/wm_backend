package com.workflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtUtil.validateToken(token)) {
                String userId = jwtUtil.extractUserId(token);
                String email = jwtUtil.extractEmail(token);
                String rol = jwtUtil.extractRol(token);

                // Crear autenticación y establecerla en el contexto de Spring Security
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null, null
                );
                auth.setDetails(token);
                SecurityContextHolder.getContext().setAuthentication(auth);

                request.setAttribute("userId", userId);
                request.setAttribute("email", email);
                request.setAttribute("rol", rol);
            }
        }

        filterChain.doFilter(request, response);
    }
}
