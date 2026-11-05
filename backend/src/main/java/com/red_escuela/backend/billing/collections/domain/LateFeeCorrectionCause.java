package com.red_escuela.backend.billing.collections.domain;

public enum LateFeeCorrectionCause {
    BACKDATED_WITHDRAWAL,
    PAYMENT_DATED_BEFORE_CHARGED_DAYS,
    PAYMENT_WITHIN_GRACE;

    public boolean isPaymentDate() {
        return this != BACKDATED_WITHDRAWAL;
    }
}
