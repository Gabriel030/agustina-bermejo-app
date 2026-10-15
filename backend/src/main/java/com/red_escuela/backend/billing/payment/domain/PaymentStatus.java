package com.red_escuela.backend.billing.payment.domain;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    APPROVED,
    REJECTED,
    REFUNDED,
    CHARGED_BACK,
    CANCELLED
}
