package com.red_escuela.backend.billing.run.domain;

public enum MonthlyInvoiceRunStatus {
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_FAILURES,
    AWAITING_REVIEW,
    FAILED
}
