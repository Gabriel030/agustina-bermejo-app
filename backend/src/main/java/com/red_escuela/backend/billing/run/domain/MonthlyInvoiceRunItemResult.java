package com.red_escuela.backend.billing.run.domain;

public enum MonthlyInvoiceRunItemResult {
    ISSUED,
    NOT_APPLICABLE,
    ALREADY_ISSUED,
    FAILED,
    RETRIES_EXHAUSTED
}
