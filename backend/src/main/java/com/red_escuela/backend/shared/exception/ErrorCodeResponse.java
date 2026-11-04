package com.red_escuela.backend.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;

import java.util.List;
import java.util.Locale;

public record ErrorCodeResponse(
        String code,
        boolean retryable,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL) String requestId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PolicyViolation> violations) {
    public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    public static final String UNAUTHENTICATED_CODE = "UNAUTHENTICATED";
    public static final String TOKEN_EXPIRED_CODE = "TOKEN_EXPIRED";
    public static final String FORBIDDEN_CODE = "FORBIDDEN";
    public static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    public static final String MALFORMED_REQUEST_BODY_CODE = "MALFORMED_REQUEST_BODY";
    public static final String INVALID_PARAMETER_TYPE_CODE = "INVALID_PARAMETER_TYPE";
    public static final String DATA_INTEGRITY_VIOLATION_CODE = "DATA_INTEGRITY_VIOLATION";
    public static final String CONCURRENT_MODIFICATION_CODE = "CONCURRENT_MODIFICATION";

    public static final String EXTERNAL_SERVICE_UNAVAILABLE_CODE = "EXTERNAL_SERVICE_UNAVAILABLE";

    private static final String EXCEPTION_SUFFIX = "Exception";
    private static final String REQUEST_ID_KEY = "requestId";

    public ErrorCodeResponse(String code) {
        this(code, false);
    }

    public ErrorCodeResponse(String code, boolean retryable) {
        this(code, retryable, MDC.get(REQUEST_ID_KEY), List.of());
    }

    public static String codeOf(Class<?> exceptionType) {
        if (CircuitOpenException.class.isAssignableFrom(exceptionType)) {
            return EXTERNAL_SERVICE_UNAVAILABLE_CODE;
        }
        String name = exceptionType.getSimpleName();
        if (name.endsWith(EXCEPTION_SUFFIX)) {
            name = name.substring(0, name.length() - EXCEPTION_SUFFIX.length());
        }
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    public static boolean isRetryable(Class<?> exceptionType) {
        return TooManyRequestsException.class.isAssignableFrom(exceptionType)
                || ExternalServiceException.class.isAssignableFrom(exceptionType);
    }

    public static ErrorCodeResponse of(Throwable exception) {
        List<PolicyViolation> violations = exception instanceof ReportsPolicyViolations reporting && reporting.violations() != null ? reporting.violations() : List.of();
        return new ErrorCodeResponse(codeOf(exception.getClass()), isRetryable(exception.getClass()), MDC.get(REQUEST_ID_KEY), violations);
    }
}
