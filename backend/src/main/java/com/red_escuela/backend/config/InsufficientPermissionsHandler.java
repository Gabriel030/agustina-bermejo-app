package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InsufficientPermissionsHandler implements AccessDeniedHandler {
    static final String FORBIDDEN_CODE = ErrorCodeResponse.FORBIDDEN_CODE;

    private final JsonErrorResponseWriter errorWriter;

    public InsufficientPermissionsHandler(JsonErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, HttpStatus.FORBIDDEN.value(), FORBIDDEN_CODE);
    }
}
