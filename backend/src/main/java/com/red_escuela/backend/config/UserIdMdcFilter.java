package com.red_escuela.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(0)
public class UserIdMdcFilter extends OncePerRequestFilter {

    public static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = currentUserId();
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_KEY);
        }
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String nombre = authentication.getName();
        return nombre == null || nombre.isBlank() || "anonymousUser".equals(nombre) ? null : nombre;
    }
}
