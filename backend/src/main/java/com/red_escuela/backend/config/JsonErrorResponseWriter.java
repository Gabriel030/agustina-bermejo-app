package com.red_escuela.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public JsonErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorCodeResponse(code));
    }
}
