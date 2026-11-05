package com.red_escuela.backend.billing.run.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class MonthlyInvoiceRunItemNotFoundException extends NotFoundException {

    public MonthlyInvoiceRunItemNotFoundException(Long runId, Long itemId) {
        super("Monthly invoice run item not found: run=" + runId + ", item=" + itemId);
    }
}
