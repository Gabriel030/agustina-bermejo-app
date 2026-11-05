package com.red_escuela.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class NoStoreResponseFilter extends OncePerRequestFilter {
    private static final String AUTH_PREFIX = "/auth/";
    private static final String ME_PATH = "/me";
    private static final String ME_PREFIX = "/me/";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isSensitive(path)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isSensitive(String path) {
        return path.startsWith(AUTH_PREFIX) || path.equals(ME_PATH) || path.startsWith(ME_PREFIX);
    }
}
