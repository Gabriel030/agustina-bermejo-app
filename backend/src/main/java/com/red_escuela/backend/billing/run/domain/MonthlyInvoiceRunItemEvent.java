package com.red_escuela.backend.billing.run.domain;

public enum MonthlyInvoiceRunItemEvent {
    RETRY_ISSUED,
    RETRY_ALREADY_ISSUED,
    RETRY_NOT_APPLICABLE,
    RETRY_FAILED_WITH_WAIT,
    RETRY_FAILED_WITHOUT_WAIT
}
