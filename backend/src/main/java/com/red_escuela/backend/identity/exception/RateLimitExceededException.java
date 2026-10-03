package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.TooManyRequestsException;

public class RateLimitExceededException extends TooManyRequestsException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
