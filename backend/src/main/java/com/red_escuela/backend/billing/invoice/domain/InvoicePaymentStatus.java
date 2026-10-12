package com.red_escuela.backend.billing.invoice.domain;

public enum InvoicePaymentStatus {
    PENDING,
    PAYMENT_UNDER_REVIEW,
    PAID,
    OVERDUE,
    CANCELLED
}
