package com.red_escuela.backend.billing.annualupfront.domain;

public enum AnnualUpfrontRequestEvent {
    REQUESTED,
    PAYMENT_INITIATED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    DEADLINE_PASSED,
    WITHDRAWN,
    CANCELLED_BY_STUDENT
}
