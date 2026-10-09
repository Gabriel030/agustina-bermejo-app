package com.red_escuela.backend.billing.payment.operation;

public interface IdempotencyKeyed {
    String idempotencyKey();
}
