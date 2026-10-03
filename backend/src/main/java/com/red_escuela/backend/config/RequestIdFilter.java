package com.red_escuela.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    static String sanitize(String recibido) {
        if (recibido == null || recibido.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String limpio = recibido.replaceAll("[^A-Za-z0-9._:-]", "");
        if (limpio.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return limpio.length() > MAX_LENGTH ? limpio.substring(0, MAX_LENGTH) : limpio;
    }
}
