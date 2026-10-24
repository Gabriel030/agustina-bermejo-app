package com.red_escuela.backend.billing.payment.domain;

import java.util.Set;

public enum RefundStatus {
    REQUESTED,
    APPROVED,
    COMPLETED,
    FAILED,
    REJECTED;

    public static final Set<RefundStatus> PENDING = Set.of(REQUESTED, APPROVED);
}
