package com.red_escuela.backend.billing.run.domain;

public enum BillingRunStep {
    GATEWAY_SYNC,
    OVERDUE_DETECTION,
    LATE_FEE_ACCRUAL,
    LATE_FEE_WITHDRAWAL_CORRECTION
}
