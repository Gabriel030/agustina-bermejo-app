package com.red_escuela.backend.billing.payment.operation;

@FunctionalInterface
public interface ReceiptFileStorage {
    String storeReceiptFileOf(Long studentId);
}
