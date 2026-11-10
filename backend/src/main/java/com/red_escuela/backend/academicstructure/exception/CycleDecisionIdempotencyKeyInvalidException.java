package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CycleDecisionIdempotencyKeyInvalidException extends BadRequestException {
    public CycleDecisionIdempotencyKeyInvalidException() {
        super("La Idempotency-Key tiene más de 100 caracteres");
    }
}
