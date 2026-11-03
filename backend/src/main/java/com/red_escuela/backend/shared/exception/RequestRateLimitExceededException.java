package com.red_escuela.backend.shared.exception;

import java.time.Duration;

public class RequestRateLimitExceededException extends TooManyRequestsException {
    private final long retryAfterSeconds;

    public RequestRateLimitExceededException(Duration retryAfter) {
        super("Request rate limit exceeded");
        this.retryAfterSeconds = retryAfter.toSeconds();
    }

    @Override
    public Duration retryAfter() {
        return Duration.ofSeconds(retryAfterSeconds);
    }
}
