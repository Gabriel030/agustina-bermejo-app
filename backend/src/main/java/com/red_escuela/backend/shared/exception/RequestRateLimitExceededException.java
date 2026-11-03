package com.red_escuela.backend.shared.exception;

import java.time.Duration;

public class RequestRateLimitExceededException extends TooManyRequestsException {
    private final Duration retryAfter;

    public RequestRateLimitExceededException(Duration retryAfter) {
        super("Request rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    @Override
    public Duration retryAfter() {
        return retryAfter;
    }
}
