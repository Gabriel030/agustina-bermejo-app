package com.red_escuela.backend.shared.event;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationIds {
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String USER_ID_KEY = "userId";
    public static final String CORRELATION_ID_KEY = "correlationId";

    private CorrelationIds() {
    }

    public static String current() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
