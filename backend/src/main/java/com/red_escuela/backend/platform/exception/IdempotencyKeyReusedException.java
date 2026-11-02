package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class IdempotencyKeyReusedException extends ConflictException {
    public IdempotencyKeyReusedException() {
        super("La Idempotency-Key ya se usó con un pedido distinto");
    }
}
