package com.red_escuela.backend.billing.payment.domain;

public enum RefundEvent {
    REFUND_REQUESTED,
    REFUND_APPROVED,
    REFUND_REJECTED,
    REFUND_COMPLETED,
    REFUND_FAILED
}
