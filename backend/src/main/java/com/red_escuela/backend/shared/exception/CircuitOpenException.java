package com.red_escuela.backend.shared.exception;

import java.time.Duration;

public class CircuitOpenException extends ExternalServiceException {
    private final Duration retryAfter;

    public CircuitOpenException(String breakerName, Duration retryAfter) {
        super("Circuit breaker " + breakerName + " is open");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
