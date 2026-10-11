package com.red_escuela.backend.billing.payment.policy;

public record IdempotencyKeyReuseContext(String storedOperationType, String storedRequestHash, String operationType, String requestHash) {
}
