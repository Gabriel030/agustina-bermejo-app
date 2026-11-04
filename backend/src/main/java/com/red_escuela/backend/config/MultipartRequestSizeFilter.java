package com.red_escuela.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MultipartRequestSizeFilter extends OncePerRequestFilter {

    public static final long DEFAULT_MAX_REQUEST_BYTES = 6L * 1024 * 1024;
    public static final long PROGRAM_APPLICATION_MAX_REQUEST_BYTES = 10L * 1024 * 1024;
    static final String PROGRAM_APPLICATION_WITH_DOCUMENTS_PATH = "/me/program-enrollment-applications/with-documents";
    private static final String REJECTION_BODY = "{\"code\":\"MAX_UPLOAD_SIZE_EXCEEDED\",\"retryable\":false}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isMultipart(request) && request.getContentLengthLong() > limitFor(request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(REJECTION_BODY);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private long limitFor(HttpServletRequest request) {
        return PROGRAM_APPLICATION_WITH_DOCUMENTS_PATH.equals(request.getRequestURI())
                ? PROGRAM_APPLICATION_MAX_REQUEST_BYTES
                : DEFAULT_MAX_REQUEST_BYTES;
    }
}
