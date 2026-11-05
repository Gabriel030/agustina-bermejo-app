package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class MonthlyInvoiceRunNotAwaitingReviewException extends ConflictException {

    public MonthlyInvoiceRunNotAwaitingReviewException(Long monthlyRunId, String status) {
        super("Monthly invoice run " + monthlyRunId + " is " + status + " and does not accept draft review");
    }
}
