package com.red_escuela.backend.shared.exception;

import java.time.Duration;

public abstract class TooManyRequestsException extends RuntimeException {
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofMinutes(1);

    protected TooManyRequestsException() {
        super();
    }

    protected TooManyRequestsException(String message) {
        super(message);
    }

    protected TooManyRequestsException(String message, Throwable cause) {
        super(message, cause);
    }

    public Duration retryAfter() {
        return DEFAULT_RETRY_AFTER;
    }
}
