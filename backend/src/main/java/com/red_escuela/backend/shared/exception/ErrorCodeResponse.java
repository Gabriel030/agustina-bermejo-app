package com.red_escuela.backend.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;
import java.util.Locale;

public record ErrorCodeResponse(String code, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PolicyViolation> violations) {
    private static final String EXCEPTION_SUFFIX = "Exception";

    public ErrorCodeResponse(String code) {
        this(code, List.of());
    }

    public static ErrorCodeResponse of(Throwable exception) {
        String name = exception.getClass().getSimpleName();
        if (name.endsWith(EXCEPTION_SUFFIX)) {
            name = name.substring(0, name.length() - EXCEPTION_SUFFIX.length());
        }
        String code = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        List<PolicyViolation> violations = exception instanceof ReportsPolicyViolations reporting ? reporting.violations() : List.of();
        return new ErrorCodeResponse(code, violations);
    }
}
