package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.TooManyRequestsException;

import java.time.Duration;

public class GuardianConfirmationResendLimitException extends TooManyRequestsException {
    private final Duration retryAfter;

    public GuardianConfirmationResendLimitException(Duration retryAfter) {
        super("Ya enviamos el correo a tu responsable hace poco, esperá un rato antes de pedirlo otra vez");
        this.retryAfter = retryAfter;
    }

    @Override
    public Duration retryAfter() {
        return retryAfter;
    }
}
