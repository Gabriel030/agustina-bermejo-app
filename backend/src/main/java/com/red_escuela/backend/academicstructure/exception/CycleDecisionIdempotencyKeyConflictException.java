package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CycleDecisionIdempotencyKeyConflictException extends ConflictException {
    public CycleDecisionIdempotencyKeyConflictException(Long cycleId) {
        super("La Idempotency-Key ya se usó para otra decisión sobre el ciclo " + cycleId);
    }
}
