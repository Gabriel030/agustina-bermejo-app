package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ApplicationDecisionIdempotencyKeyConflictException extends ConflictException {
    public ApplicationDecisionIdempotencyKeyConflictException(Long applicationId) {
        super("La Idempotency-Key ya se usó para otra decisión sobre la solicitud " + applicationId);
    }
}
