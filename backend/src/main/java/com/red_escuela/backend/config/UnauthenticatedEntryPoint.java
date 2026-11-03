package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {
    static final String REJECTED_TOKEN_ATTRIBUTE = UnauthenticatedEntryPoint.class.getName() + ".REJECTED_TOKEN";
    static final String TOKEN_EXPIRED_CODE = ErrorCodeResponse.TOKEN_EXPIRED_CODE;
    static final String UNAUTHENTICATED_CODE = ErrorCodeResponse.UNAUTHENTICATED_CODE;

    private final JsonErrorResponseWriter errorWriter;

    public UnauthenticatedEntryPoint(JsonErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        boolean tokenRejected = Boolean.TRUE.equals(request.getAttribute(REJECTED_TOKEN_ATTRIBUTE));
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                tokenRejected ? TOKEN_EXPIRED_CODE : UNAUTHENTICATED_CODE);
    }
}
