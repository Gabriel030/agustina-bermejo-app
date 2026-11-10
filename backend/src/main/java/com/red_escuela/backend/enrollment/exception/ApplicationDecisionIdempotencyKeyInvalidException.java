package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class ApplicationDecisionIdempotencyKeyInvalidException extends BadRequestException {
    public ApplicationDecisionIdempotencyKeyInvalidException() {
        super("La Idempotency-Key tiene más de 100 caracteres");
    }
}
